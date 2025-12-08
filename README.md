<!--suppress HtmlDeprecatedTag, XmlDeprecatedElement -->
<center>
<img alt="surveyor banner" src="https://cdn.modrinth.com/data/4KjqhPc9/images/036db8bcb021c9e81e18561698d45d3c7bb71127.png"><br/>
An open-source backend (and API) for world map and waypoint mods!<br/>
<b>Requires <a href="https://modrinth.com/mod/connector">Connector</a> and <a href="https://modrinth.com/mod/forgified-fabric-api">FFAPI</a> on (neo)forge.<br/></b>
</center>

---

**Surveyor** is the **map backend** for mods like [Antique Atlas 4](https://modrinth.com/mod/antique-atlas-4), [Hoofprint](https://modrinth.com/mod/hoofprint), and [Via Romana](https://modrinth.com/mod/via-romana).<br/>
Along with handling the generation and saving of map data like terrain and waypoints, Surveyor:
- Shows other players on your map, even hundreds of chunks away!
- Tracks your exploration, and will restore your map data from the server if it's lost or you change computers!
- Enables **live map sharing** with other players of your choosing - terrain, waypoints, the lot!
- Allows swapping map frontends any time without losing your map data!
- Imports waypoints from Xaero's Minimap, and has integration for mods like [Waystones](https://modrinth.com/mod/surveystones) and [OPAC](https://modrinth.com/mod/surveyalot).
- Is fully modular - so mods like [Atlas HUD](https://modrinth.com/mod/antique-atlas-compass-hud) can utilize waypoints without enabling terrain scanning.

### Commands

If you're a server admin or don't have a map frontend installed, surveyor comes builtin with a few helpful commands.
- `/surveyor` displays summary of how many chunks and structures you've explored, and waypoints recorded.
- `/surveyor share [player]` and `/surveyor unshare` allows joining and leaving map sharing groups.
- `/waypoints` allows viewing and editing your recorded waypoints.
- `/landmarks` allows viewing and (op 2 or above) editing global waypoints.

### Configuration

Surveyor's configuration can be edited in `config/surveyor.toml`, or in-game using [McQoy](https://modrinth.com/mod/mcqoy). This includes:
- Toggling the terrain, structure, and landmark subsystems (otherwise set by installed frontends on clients)
- What data should be networked to clients / the server, and between group members.
- Whether all players on the server should be considered part of one global map sharing group.
- How often to send player position updates to clients, and how fast to restore terrain to clients with missing data.

### Mod Developers

Feel free to reach out if you'd like to develop something with surveyor! Or don't, we don't mind! We appreciate:
- PRs making shots at [surveyor enhancements and bugs](https://github.com/sisby-folk/surveyor/issues?q=is%3Aissue%20state%3Aopen%20(label%3Abug%20OR%20label%3Aenhancement)).
- PRs containing ports to an older established versions (1.4.7, 1.17.10, 1.12.2, 1.16.5, 1.18.2) - [_or latest, i guess..._](https://github.com/sisby-folk/surveyor/issues/91)
- PRs containing API features that you'd benefit from - ideally post an issue first, and we can workshop and label it!
- Compat addons, world maps, minimaps, and waypoint frontends utilizing surveyor! Serverside, web map, minecraftless... Surveyor is all about cultivating fun new things in the map mod space.

Check out the [frontend dev guide](https://github.com/sisby-folk/surveyor/blob/1.20/README.md) for a breakdown of the complicated parts of the internals. <br/>

If you've made something, hit us up and we'll link it here! We'll also answer questions for any in-progress project.<br/>
You can reach out to us through the [modfest discord](https://discord.gg/gn543Ee) (#projects->Surveyor), on [mastodon](https://tech.lgbt/@sleepingdragoninn), or hell, via [email](mailto:sleepingdragoninn@gmail.com).

## Afterword

Surveyor was built on the thoughts, advice, opinions, and past works of many modders in the community.

Thank you to everyone who's been involved and helped to make this project happen, both directly and by fluttering about the sidelines - trust me, that's a lot of you!

We made this mod because it sounded cool - we hope it helps other artists and mod devs to make cool things too!
