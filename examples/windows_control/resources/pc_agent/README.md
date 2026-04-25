`operit-pc-agent/` is the source of truth for the Windows agent asset.

`manifest.json` declares it as a directory resource (`inode/directory`).
When `ToolPkg.readResource("pc_agent_zip")` is called, the runtime exports this
folder as a zip file automatically, so no separate pre-packaging step is needed.
