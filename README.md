# RadarJammer [![Build Status](http://vps40435.vps.ovh.ca:8080/job/RadarJammer/badge/icon)](http://vps40435.vps.ovh.ca:8080/job/RadarJammer/)
Radar jamming plugin for Bukkit/Spigot

Works with 1.8 and 1.9

Configuration:

|Option|Value|
|---|---|
|vFov|The max vertical view angle, default: 35|
|hFov|The max horizontal view angle, default: 60|
|minCheck| The minimum view check distance, players closer than this will be shown no matter what, default: 10|
|maxCheck|The distance at which angle checking stops, 'auto' will calculate a maxCheck based on server view distance|
|showCombatTagged|If true will show combat tagged players regardless of other settings, default: true|
|trueInvis|Will hide invis players even if they have armor or items, default: false|
|blindDuration|The duration of blinding in minutes, default: 3|
|maxSpin|The maximum degrees a player can turn per second before being flagged, default: 500|
|maxFlags|The maximum number of flags per minute before being blinded, default: 55 (note that this defaults to someone failing 55/60 checks, decrease to be more harsh, increase to be more permissive but dont make it greater than 60 it would never flag|
|flagTime|The time in between checks to flag, default: 60|
|timing|Enables timing calculations and debug info, default: false|
|maxLogoutTime|How long, in ticks, a player should be logged out before we get rid of allocation for them, default: 600000 (10 minutes)|
