# Changelog

## 0.7.41 (2015-12-31)
- Print date and time at startup to ensure logfile uniqueness

## 0.7.40
- Accidental release, no changes.

## 0.7.39 (2015-12-21)

- Updated geom2gtfs to solve concurrency issues

## 0.7.36 (2015-12-07)

- Secure cookies prevent man-in-the-middle attacks, changing session ID prevents session fixation

## 0.7.30 (2015-11-13)

- Upgrade to version of OTP that calculates averages accurately by not including extrema in averages (mostly relevant to mixed schedules/frequency networks).

## 0.7.26

- New slider (and new version of OTP to match) to control reachability threshold, allowing excluding destinations that are only reachable at a few
  minutes of the time window.