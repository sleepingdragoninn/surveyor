<!--suppress HtmlDeprecatedTag, XmlDeprecatedElement -->
<center>
<img alt="surveyor banner" src="https://cdn.modrinth.com/data/4KjqhPc9/images/036db8bcb021c9e81e18561698d45d3c7bb71127.png"><br/>
An open-source backend (and API) for co-op-enabled world map and waypoint mods!<br/>
<b>Requires <a href="https://modrinth.com/mod/connector">Connector</a> and <a href="https://modrinth.com/mod/forgified-fabric-api">FFAPI</a> on (neo)forge.<br/></b>
</center>

---

**Surveyor** is the backend for map mods like [Antique Atlas 4](https://modrinth.com/mod/antique-atlas-4), [Hoofprint](https://modrinth.com/mod/hoofprint), and [Dead Reckoning](https://modrinth.com/mod/dead-reckoning).<br/>

**When installed client-side, Surveyor allows you to:**
- View explored terrain and mark points of interest with waypoints
- See on-map landmarks for other mods via compatibility addons, like for [Waystones](https://modrinth.com/mod/surveystones) and [OPAC](https://modrinth.com/mod/surveyalot)
- Swap map frontends at any time, retaining all map data
- Automatically migrate waypoints directly from an existing Xaero's Minimap save

**When installed on both sides, Surveyor allows you to:**
- Explore map terrain and edit waypoints in co-operative groups **(server-wide by default)**
- See other players on your map, even far away or in other dimensions
- Swap instances or computers, with your map data restored from the server
- Use op commands to create server-wide landmarks for POIs like spawn, shops, warp hubs, etc.
- Copy exploration from and to vanilla maps by sneak+using a cartography table (style configurable)

![vanilla map integration preview](https://cdn.modrinth.com/data/4KjqhPc9/images/e888d799c43d38ff5665c39fc844286ee05d5ed0.png)

**As a mod developer, Surveyor allows you to:**
- Create **pure map frontends** by handling all map data itself - terrain, structures, and waypoints
- Create addons for custom on-map landmarks, with a flexible component-style data system
- Create any kind of frontend - server-optional, ingame/mixed-side, external/browser-based, etc
- Utilize the waypoint system on its own, for small, lightweight waypoint/compass mods

### Configuration

Surveyor can be configured via `config/surveyor.toml`, [McQoy](https://modrinth.com/mod/mcqoy), or [QoMC](https://modrinth.com/mod/qomc). Most changes require a restart.
- Disable **server-wide co-op** by setting `globalSharing=false` (**recommended for public servers**)
- Tweak what data is synchronized between players, groups, and the server in the `Networking` section
- Toggle builtin automatic waypoints (e.g. nether portals, grave markers) in the `Builtins` section

### Commands

Surveyor can mostly be interacted with via map frontends, but comes with a few useful commands:
- When `globalSharing=false`, use `/surveyor share/unshare` to form groups to share waypoints and map data
- Use `/surveyor`, `/waypoints`, and `/landmarks` to review your waypoints and explored map data
- Use `/waypoints new/remove` to create/edit custom waypoints (e.g. if a frontend is not installed)
- Use `/landmarks new/remove` (op 2) to create/edit operator-level global waypoints (e.g. for server spawn)

## Troubleshooting

Report any unique replicable issue to the [issues page](https://github.com/sisby-folk/surveyor/issues) with logs, screenshots, and replication steps where possible.

Client data is stored in `.minecraft/data/surveyor/[world]/[dim]`, singleplayer/server data in `[world]/[dim]/data/surveyor`<br/>
`c.X.X` files contain terrain regions, `s.X.X` files contain structures, and `landmarks.dat` contains waypoint data.

When encountering issues, you might like to try any of the following:
- Backing up the affected data and deleting it (e.g. for a structure-related error, wipe structure data)
- Disabling networking for a subsystem (e.g. for a terrain packet client kick, set `networking.terrain` to `NONE`)
- Disabling a subsystem (e.g. for extreme server memory usage, set `terrain` to `DISABLED` on the server)

## Contributions / Addons

Feel free to reach out if you'd like to develop something with surveyor - or just go for it! We appreciate:
- PRs making shots at [surveyor enhancements and bugs](https://github.com/sisby-folk/surveyor/issues?q=is%3Aissue%20state%3Aopen%20(label%3Abug%20OR%20label%3Aenhancement))
- PRs containing ports to an older established versions (1.4.7, 1.17.10, 1.12.2, 1.16.5, 1.18.2) - [or latest, per policy](https://github.com/sisby-folk/surveyor/issues/91)
- PRs containing API features that you'd benefit from - ideally post an issue first, and we can workshop it
- Compat addons, world maps, minimaps, and waypoint frontends utilizing surveyor!

Check out the [frontend dev guide](https://github.com/sisby-folk/surveyor/blob/1.20/FRONTENDS.md) for a breakdown of the complicated parts of the internals. <br/>

### Licensing / Credit

Please match your addon licenses to LGPLv3 if possible - it helps improve the surveyor ecosystem!<br/>
(LGPLv3 is a copyleft license, so this is required for anything directly adapted from Surveyor)

If you've written something, hit us up and we might link it here! We'll also answer questions for in-progress projects.<br/>
Devs can reach out on [mastodon](https://tech.lgbt/@sleepingdragoninn) or via [email](mailto:sleepingdragoninn@gmail.com).

## Afterword

Surveyor was built on the thoughts, advice, opinions, and past works of many modders in the community.

Thanks to everyone who helped make this project happen, even just by fluttering on the sidelines - it means a lot.

We made surveyor because it sounded cool - we hope it helps other artists/modders to make cool things!
