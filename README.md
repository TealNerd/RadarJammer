# RadarJammer [![Build Status](http://vps40435.vps.ovh.ca:8080/job/RadarJammer/badge/icon)](http://vps40435.vps.ovh.ca:8080/job/RadarJammer/)
Radar jamming plugin for Bukkit/Spigot

Configuration:

|Option|Value|
|---|---|
|vFov|The max vertical view angle, default: 35.0*|
|hFov|The max horizontal view angle, default: 60.0*|
|minCheck| The minimum view check distance, players closer than this will be shown no matter what, default: 14|
|maxCheck|The distance at which angle checking stops, 'auto' will calculate a maxCheck based on server view distance|

*Note that the vFov and hFov values are used to calculate a third number used for actual calculations. This results in a conical FOV with the corners of the actual FOV touching the edge of the cone. This allows players to "see" others slightly outside their actual FOV. This helps make the math easier and helps reduce players appearing abruptly (because they were already there outside the FOV)
