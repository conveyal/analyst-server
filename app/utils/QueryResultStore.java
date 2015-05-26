package utils;

import com.beust.jcommander.internal.Sets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import controllers.Application;
import models.Query;
import models.Shapefile;
import org.mapdb.*;
import org.opentripplanner.analyst.Histogram;
import org.opentripplanner.analyst.ResultSet;
import play.Play;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A datastore optimized for storing query results.
 * This is not a MapDB: it uses one flat file per variable (since you're only ever looking at one variable at a time),
 * which is then encoded like this:
 *
 * Header: QUERYRESULT encoded as UTF
 * Query ID encoded as UTF
 * Variable name encoded as UTF
 * Envelope parameter encoded as a UTF.
 * Repeated:
 *   Feature ID encoded as string
 *   	int number of counts (also number of sums)
 *     counts encoded as ints
 *     sums encoded as ints
 */
public class QueryResultStore {
	public final boolean readOnly;
	private List<String> attributes;

	private File outDir;

	private String queryId;

	/** cache filewriters */
	private Map<Fun.Tuple2<String, ResultEnvelope.Which>, FileWriter> writerCache = Maps.newHashMap();

	/** we need to keep references to these because we need to close them */
	private Collection<FileReader> readerCache = Lists.newArrayList();
	
	public QueryResultStore (Query q) {
		this(q.id, q.completePoints == q.totalPoints, new File(Play.application().configuration().getString("application.data"), "flat_results"));
	}

	public QueryResultStore(String queryId, boolean readOnly, File outDir) {
		this.readOnly = readOnly;
		this.queryId = queryId;

		this.outDir = outDir;
		outDir.mkdirs();
	}
	
	/** Save a result envelope in this store */
	public void store(ResultEnvelope res) {
		if (readOnly)
			throw new UnsupportedOperationException("Attempt to write to read-only query result store!");

		for (ResultEnvelope.Which which : ResultEnvelope.Which.values()) {
			ResultSet rs = res.get(which);

			if (rs != null) {
				// parallelize across variables, which are stored in different files, so this is threadsafe
				rs.histograms.entrySet().parallelStream().forEach(e -> {
					getWriter(e.getKey(), which).write(res.id, e.getValue());
				});
			}
		}
	}

	private FileWriter getWriter(String variable, ResultEnvelope.Which which) {
		Fun.Tuple2<String, ResultEnvelope.Which> wkey = new Fun.Tuple2<>(variable, which);

		if (!writerCache.containsKey(wkey)) {
			synchronized (writerCache) {
				if (!writerCache.containsKey(wkey)) {
					String filename = String.format(Locale.US, "%s_%s_%s.results.gz", queryId, variable, which);
					writerCache.put(wkey, new FileWriter(new File(outDir, filename), queryId, variable, which));
				}
			}
		}

		return writerCache.get(wkey);
	}

	/** close the underlying datastore, writing all changes to disk */
	public void close () {
		for (FileWriter w : writerCache.values()) {
			w.close();
		}

		for (FileReader reader : readerCache) {
			reader.close();
		}
	}
	
	/** get all the resultsets for a particular variable and envelope parameter */
	public Iterator<ResultSet> getAll(String attr, ResultEnvelope.Which which) {
		String filename = String.format(Locale.US, "%s_%s_%s.results.gz", queryId, attr, which);
		// cannot return a cached reader as each one has a pointer into the file
		FileReader r = new FileReader(new File(outDir, filename));
		readerCache.add(r);
		return r;
	}

	/**
	 * Await results for query q.
	 *
	 * Note that this does not have to be run on the same machine as the UI.
	 */
	public static void accumulate (final Query query) {
		QueueManager qm = QueueManager.getManager();

		final QueryResultStore store = new QueryResultStore(query);
		final Set<String> receivedResults = Sets.newHashSet();

		// note that callback will never be called in parallel as there is a synchronized block in the
		// queue manager.
		qm.registerJobCallback(query, re -> {
			// don't save twice
			if (receivedResults.contains(re.id))
				return true;

			store.store(re);

			receivedResults.add(re.id);

			query.completePoints = receivedResults.size();

			if (query.completePoints % 200 == 0)
				query.save();

			if (query.completePoints == query.totalPoints) {
				// TODO write to S3 here so that this can be separated from the UI.
				query.save();
				store.close();
				// stop iteration, remove callback
				return false;
			}

			return true;
		});
	}

	/** Write resultsets to a flat file */
	private static class FileWriter {
		private final DataOutputStream out;

		public FileWriter(File file, String queryId, String variable, ResultEnvelope.Which which) {

			try {
				OutputStream os = new FileOutputStream(file);
				GZIPOutputStream gos = new GZIPOutputStream(os);
				// buffer for performance
				BufferedOutputStream bos = new BufferedOutputStream(gos);
				out = new DataOutputStream(bos);
				out.writeUTF("QUERYRESULT");
				out.writeUTF(queryId);
				out.writeUTF(variable);
				out.writeUTF(which.toString());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		public synchronized void write (String id, Histogram histogram) {
			if (id == null)
				throw new NullPointerException("Feature ID is null!");

			if (histogram.counts.length != histogram.sums.length)
				throw new IllegalArgumentException("Invalid histogram, sum and count lengths differ");

			try {
				out.writeUTF(id);
				out.writeInt(histogram.counts.length);

				for (int i = 0; i < histogram.counts.length; i++) {
					// TODO: zigzag encoding
					// These are marginals so they are already effectively delta-coded
					out.writeInt(histogram.counts[i]);
				}

				for (int i = 0; i < histogram.sums.length; i++) {
					out.writeInt(histogram.sums[i]);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		public void close () {
			try {
				out.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/** read resultsets from a flat file */
	private static class FileReader implements Iterator<ResultSet> {
		private DataInputStream in;

		private String var;

		public final String queryId;

		public final ResultEnvelope.Which which;

		public FileReader(File file) {
			try {
				InputStream is = new FileInputStream(file);
				GZIPInputStream gis = new GZIPInputStream(is);
				BufferedInputStream bis = new BufferedInputStream(gis);
				in = new DataInputStream(bis);

				// make sure we have a query result file, and record variable name
				String header = in.readUTF();

				if (!"QUERYRESULT".equals(header))
					throw new IllegalArgumentException("Attempt to read non-query-result file");

				queryId = in.readUTF();
				var = in.readUTF();
				which = ResultEnvelope.Which.valueOf(in.readUTF());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public boolean hasNext() {
			// this cannot be the easiest way to tell if we're at the end of the file
			in.mark(8192);

			try {
				in.readUTF();
			} catch (EOFException e) {
				this.close();
				return false;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			try {
				in.reset();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			return true;
		}

		@Override
		public ResultSet next() {
			if (!hasNext())
				throw new NoSuchElementException();

			try {
				ResultSet rs = new ResultSet();
				// read the ID
				rs.id = in.readUTF();

				// number of bins in the histogram
				int size = in.readInt();

				int[] counts = new int[size];

				for (int i = 0; i < size; i++) {
					counts[i] = in.readInt();
				}

				int[] sums = new int[size];

				for (int i = 0; i < size; i++) {
					sums[i] = in.readInt();
				}

				Histogram h = new Histogram();
				h.sums = sums;
				h.counts = counts;

				rs.histograms.put(var, h);

				return rs;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void forEachRemaining(Consumer<? super ResultSet> consumer) {
			while (hasNext())
				consumer.accept(next());
		}

		public void close () {
			try {
				in.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
