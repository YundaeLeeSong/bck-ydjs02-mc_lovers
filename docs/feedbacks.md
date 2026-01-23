What you described is 100% achievable with Paper + plugins, and this is precisely how large cross-play servers (Java + Bedrock) do “mod-like” experiences without client installs.

Below is a clear, realistic implementation plan and methodology, not just a plugin list. Think of this as an engineering blueprint.

High-level architecture (mental model)

You are building a content illusion system, not real blocks.

Paper Server
├─ Core Gameplay Plugin (your code)
│   ├─ Custom item system
│   ├─ Fake block / furniture system
│   ├─ Entity-based animals & fish
│   ├─ Interaction engine (click/sneak/etc.)
│   └─ GUI framework
│
├─ Resource Pack (mandatory)
│   ├─ Textures
│   ├─ Models
│   ├─ Sounds
│   └─ Fonts
│
├─ Geyser + Floodgate
│   └─ Bedrock forms / UI mapping
│
└─ Optional helper plugins
    ├─ ProtocolLib
    ├─ Citizens
    ├─ ItemsAdder-like concepts (you reimplement)
    └─ ModelEngine-like concepts

Core methodology (IMPORTANT)
Rule #1: Server owns logic, client owns visuals

Server decides what happens

Resource pack decides how it looks

Client stays vanilla

Everything you build follows this.

Step-by-step implementation plan
PHASE 1 — Resource Pack (do this FIRST)

Everything else depends on this.

What to put in the pack

Custom fish models

Animal variants (reskins)

Furniture models

Machine models

GUI icons

Fonts (unicode font mapping)

Sounds (ambient, machine hum, UI click)

Key techniques

CustomModelData

Item-based models (stick, carrot_on_a_stick, paper)

Block-model-with-entity illusion (via item frames or entities)

Folder focus
assets/minecraft/models/item/
assets/minecraft/textures/
assets/minecraft/font/
assets/minecraft/sounds.json

Server enforcement

Use PlayerResourcePackStatusEvent

Kick or restrict players who decline (optional)

Geyser supports resource packs (critical for Bedrock)

PHASE 2 — Custom Item System (foundation)

This is your core abstraction.

Design

Create a wrapper class:

class CustomItem {
  String id;
  Material baseMaterial;
  int customModelData;
  Consumer<PlayerInteractEvent> onUse;
}

Implementation

Store ID in PersistentDataContainer

Validate items on interaction

Block vanilla behavior if needed

Result

Custom tools

Fish items

Furniture items

Machine items

This replaces “mod items”.

PHASE 3 — Fake blocks & furniture
Techniques

Choose based on complexity:

Simple furniture

Invisible armor stands

Display entities (1.19+)

Item frames (fixed + invisible)

Interaction

Ray trace from player

Match entity UUID

Trigger custom logic

Persistence

Store locations in SQLite / YAML / JSON

Re-spawn entities on server start

Example use cases

Chairs

Tables

Lamps

Aquariums

PHASE 4 — Animals & fish
How this works

Use vanilla mobs

Apply:

Custom name (hidden)

Custom model via resource pack

AI tweaks

Metadata tags

Fish

Use:

Cod/Salmon/Tropical fish

Override model

Biome-based spawning logic

Animals

Variant system:

enum AnimalVariant {
  RED_FOX,
  WHITE_FOX,
  BLUE_FOX
}

Behavior

Cancel breeding

Custom drops

Custom sounds

PHASE 5 — Fake machines

This is where Paper shines.

Machine = state machine
IDLE -> PROCESSING -> OUTPUT

Example machine

Furnace-like device

Input slot (GUI)

Timer

Output slot

Animation via model swap or sound

Storage

Block location

Machine type

Current state

Timer progress

Implementation

Inventory GUI

Scheduled tasks

Cancel vanilla block interaction

PHASE 6 — GUI framework
Java (Paper)

Inventory GUIs

Click handling

Pagination

Animations (slot swapping)

Bedrock (Geyser)

Forms (simple, modal, custom)

Map GUIs to forms where possible

Fallback inventory GUIs if needed

Abstraction idea
interface Menu {
  void open(Player p);
}

PHASE 7 — Input & interaction engine
Inputs you CAN use

Right click

Left click

Sneak

Sprint

Swap hand

Drop key (detected server-side)

Combine inputs

Example:

Sneak + RightClick + CustomItem = Action

Cancel vanilla

Cancel interaction events

Replace behavior fully

PHASE 8 — Fonts & polish
Custom fonts

Unicode private range

Icons in GUIs

Text-based progress bars

Decorative UI

Sounds

Contextual sounds

Machine loops

Fish splashes

UI feedback

Tech stack recommendations
Libraries / APIs

Paper API (obvious)

Adventure (text, fonts)

ProtocolLib (optional but powerful)

Citizens (NPC dialogs)

Geyser API (Bedrock detection)

Storage

SQLite for machines & furniture

YAML for config

JSON for content definitions

Development strategy (very important)
Start small

One custom item

One GUI

One machine

One fish variant

Then generalize

Data-driven configs

Reusable systems

Content packs without recompiling

This works perfectly since my goals are
1. Zero client setup
2. Java + Bedrock compatibility
3. Stability
4. Hosting at scale

and I do not need
1. Real new blocks
2. True modded gameplay
3. Client performance boosts