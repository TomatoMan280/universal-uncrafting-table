import base64
import os
from playwright.sync_api import sync_playwright

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
RENDERS_DIR = os.path.join(BASE_DIR, "renders")
SPRITES_DIR = os.path.join(BASE_DIR, "sprites")
OUT_DIR = os.path.join(BASE_DIR, "output")
os.makedirs(OUT_DIR, exist_ok=True)


def b64_img(path):
    with open(path, "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode("ascii")


def r(name):
    return b64_img(os.path.join(RENDERS_DIR, f"{name}.png"))


def s(name):
    return b64_img(os.path.join(SPRITES_DIR, f"{name}.png"))


COMMON_CSS = """
* { box-sizing: border-box; margin: 0; padding: 0; }
html, body {
  width: 1920px; height: 1080px; overflow: hidden;
  font-family: 'Segoe UI', 'Bahnschrift', system-ui, -apple-system, sans-serif;
  background: #0c1117;
  color: #f3f6fa;
  -webkit-font-smoothing: antialiased;
}
.canvas {
  position: relative;
  width: 1920px; height: 1080px;
  background:
    radial-gradient(circle at 18% 20%, rgba(44, 99, 58, 0.28), transparent 46%),
    radial-gradient(circle at 82% 78%, rgba(37, 99, 235, 0.20), transparent 48%),
    radial-gradient(circle at 75% 15%, rgba(245, 158, 11, 0.12), transparent 40%),
    linear-gradient(145deg, #0b1016 0%, #121923 55%, #0e141c 100%);
  padding: 54px 68px;
  display: flex;
  flex-direction: column;
}
.grid-bg {
  position: absolute; inset: 0; pointer-events: none;
  background-image:
    linear-gradient(to right, rgba(116, 168, 129, 0.065) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(116, 168, 129, 0.065) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse at center, black 45%, transparent 92%);
}
.topbar {
  position: relative; z-index: 2;
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 34px;
}
.title-group h1 {
  font-family: 'Bahnschrift', 'Segoe UI', sans-serif;
  font-size: 52px; font-weight: 800; letter-spacing: 0.02em;
  line-height: 1.05;
  background: linear-gradient(90deg, #ffffff 0%, #d8f3dc 70%, #86efac 100%);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  text-transform: uppercase;
}
.title-group p {
  margin-top: 8px;
  font-size: 23px; color: #9fb0c3; font-weight: 400;
}
.brand-pill {
  display: inline-flex; align-items: center; gap: 14px;
  padding: 12px 22px;
  border-radius: 999px;
  background: rgba(20, 29, 39, 0.88);
  border: 1.5px solid rgba(116, 168, 129, 0.42);
  box-shadow: 0 10px 26px rgba(0, 0, 0, 0.35);
  font-size: 20px; font-weight: 700; color: #d8f3dc;
  letter-spacing: 0.04em;
}
.brand-pill img { width: 36px; height: 36px; object-fit: contain; }
.card {
  position: relative; z-index: 2;
  background: linear-gradient(180deg, rgba(24, 33, 44, 0.90) 0%, rgba(16, 23, 31, 0.94) 100%);
  border: 1.5px solid rgba(148, 163, 184, 0.22);
  border-radius: 22px;
  box-shadow: 0 22px 54px rgba(0, 0, 0, 0.48), inset 0 1px 0 rgba(255, 255, 255, 0.08);
}
.px { image-rendering: pixelated; }
.mc-slot {
  width: 84px; height: 84px;
  background: #23272c;
  border-top: 3px solid #121417;
  border-left: 3px solid #121417;
  border-right: 3px solid #3c424a;
  border-bottom: 3px solid #3c424a;
  border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  position: relative;
}
.mc-slot img { width: 64px; height: 64px; object-fit: contain; }
.mc-slot .cnt {
  position: absolute; right: 6px; bottom: 2px;
  font-family: 'Consolas', monospace;
  font-size: 22px; font-weight: 800; color: #fff;
  text-shadow: 2px 2px 0 #111;
}
.badge {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 6px 14px; border-radius: 999px;
  font-size: 16px; font-weight: 700; letter-spacing: 0.04em;
  text-transform: uppercase;
}
.badge-green { background: rgba(44, 99, 58, 0.40); border: 1px solid rgba(116, 168, 129, 0.6); color: #86efac; }
.badge-blue  { background: rgba(37, 99, 235, 0.24); border: 1px solid rgba(96, 165, 250, 0.55); color: #93c5fd; }
.badge-red   { background: rgba(239, 68, 68, 0.22); border: 1px solid rgba(248, 113, 113, 0.55); color: #fca5a5; }
.badge-amber { background: rgba(245, 158, 11, 0.22); border: 1px solid rgba(251, 191, 36, 0.55); color: #fde68a; }
.badge-purple{ background: rgba(168, 85, 247, 0.22); border: 1px solid rgba(192, 132, 252, 0.55); color: #e9d5ff; }
"""


def html_logo():
    return f"""<!DOCTYPE html><html><head><meta charset="utf-8"><style>
* {{ box-sizing: border-box; margin: 0; padding: 0; }}
html, body {{ width: 800px; height: 800px; overflow: hidden; background: #0b1116; }}
.wrap {{
  position: relative; width: 800px; height: 800px;
  background:
    radial-gradient(circle at 50% 48%, #214a30 0%, #152b20 46%, #0c141c 100%);
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
}}
.mat-grid {{
  position: absolute; inset: 0;
  background-image:
    linear-gradient(to right, rgba(116, 168, 129, 0.24) 3px, transparent 3px),
    linear-gradient(to bottom, rgba(116, 168, 129, 0.24) 3px, transparent 3px);
  background-size: 80px 80px;
  background-position: 40px 40px;
  mask-image: radial-gradient(circle at center, rgba(0,0,0,0.8) 25%, transparent 82%);
}}
.frame {{
  position: absolute; inset: 22px;
  border-radius: 56px;
  border: 6px solid rgba(116, 168, 129, 0.45);
  box-shadow: inset 0 0 50px rgba(0,0,0,0.55), 0 0 30px rgba(74, 222, 128, 0.15);
}}
.glow {{
  position: absolute; width: 560px; height: 560px; border-radius: 50%;
  background: radial-gradient(circle, rgba(74, 222, 128, 0.26) 0%, rgba(59, 130, 246, 0.14) 52%, transparent 72%);
}}
.arc-svg {{
  position: absolute; inset: 0; width: 800px; height: 800px;
}}
.table-img {{
  position: relative; z-index: 3;
  width: 630px; height: 630px; object-fit: contain;
  margin-top: 14px;
  filter: drop-shadow(0 28px 28px rgba(0, 0, 0, 0.72));
}}
</style></head><body>
<div class="wrap">
  <div class="mat-grid"></div>
  <div class="glow"></div>
  <svg class="arc-svg" viewBox="0 0 800 800" fill="none">
    <circle cx="400" cy="400" r="315" stroke="rgba(134, 239, 172, 0.25)" stroke-width="6" stroke-dasharray="22 14"/>
    <path d="M 185 205 A 295 295 0 0 1 615 205" stroke="#4ade80" stroke-width="14" stroke-linecap="round" opacity="0.82"/>
    <polygon points="628,220 595,180 645,172" fill="#4ade80" opacity="0.9"/>
    <path d="M 615 595 A 295 295 0 0 1 185 595" stroke="#60a5fa" stroke-width="14" stroke-linecap="round" opacity="0.82"/>
    <polygon points="172,580 205,620 155,628" fill="#60a5fa" opacity="0.9"/>
  </svg>
  <div class="frame"></div>
  <img class="table-img" src="{r('ut_logo')}" />
</div></body></html>"""


def html_hero():
    return f"""<!DOCTYPE html><html><head><meta charset="utf-8"><style>{COMMON_CSS}
.hero-layout {{
  position: relative; z-index: 2;
  flex: 1; display: grid; grid-template-columns: 1.04fr 0.96fr; gap: 48px; align-items: center;
}}
.pill-row {{ display: flex; gap: 12px; margin-bottom: 24px; }}
.hero-title {{
  font-family: 'Bahnschrift', 'Segoe UI', sans-serif;
  font-size: 86px; font-weight: 800; line-height: 0.96; letter-spacing: -0.01em;
  text-transform: uppercase;
  background: linear-gradient(135deg, #ffffff 0%, #e2f7e8 55%, #6ee7b7 100%);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
}}
.hero-sub {{
  margin-top: 22px; font-size: 27px; line-height: 1.42; color: #b6c4d4; max-width: 820px;
}}
.feat-grid {{
  margin-top: 38px; display: grid; grid-template-columns: 1fr 1fr; gap: 18px;
}}
.feat-item {{
  background: rgba(20, 29, 40, 0.85);
  border: 1.5px solid rgba(116, 168, 129, 0.28);
  border-radius: 16px; padding: 18px 22px;
  display: flex; align-items: center; gap: 16px;
}}
.feat-icon {{
  width: 54px; height: 54px; border-radius: 12px;
  background: rgba(44, 99, 58, 0.35); border: 1px solid rgba(134, 239, 172, 0.45);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}}
.feat-icon img {{ width: 40px; height: 40px; object-fit: contain; }}
.feat-item h3 {{ font-size: 21px; font-weight: 700; color: #f1f5f9; }}
.feat-item p {{ font-size: 16px; color: #94a3b8; margin-top: 3px; }}

.stage {{
  position: relative; height: 920px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}}
.stage-ring {{
  position: absolute; width: 690px; height: 690px; border-radius: 50%;
  background: radial-gradient(circle, rgba(74, 222, 128, 0.22) 0%, rgba(37, 99, 235, 0.14) 55%, transparent 72%);
  border: 2px dashed rgba(134, 239, 172, 0.22);
}}
.hero-table {{
  position: relative; z-index: 3; width: 560px; height: 560px; object-fit: contain;
  margin-top: 68px;
  filter: drop-shadow(0 38px 34px rgba(0,0,0,0.72));
}}
.flow-top {{
  position: absolute; top: 28px; left: 50%; transform: translateX(-50%); z-index: 5;
  display: flex; align-items: center; gap: 16px;
  padding: 16px 24px; border-radius: 20px;
  background: rgba(15, 22, 31, 0.92);
  border: 1.5px solid rgba(148, 163, 184, 0.28);
  box-shadow: 0 18px 40px rgba(0,0,0,0.55);
}}
.flow-arrow {{
  font-size: 32px; font-weight: 900; color: #4ade80; padding: 0 6px;
}}
.float-chip {{
  position: absolute; z-index: 6;
  padding: 12px 18px; border-radius: 14px;
  background: rgba(18, 27, 38, 0.94);
  border: 1.5px solid rgba(116, 168, 129, 0.45);
  box-shadow: 0 14px 30px rgba(0,0,0,0.5);
  display: flex; align-items: center; gap: 12px;
  font-size: 18px; font-weight: 700; color: #e2e8f0;
}}
.float-chip img {{ width: 42px; height: 42px; object-fit: contain; padding: 3px; border-radius: 8px; background: rgba(255,255,255,0.06); }}
</style></head><body>
<div class="canvas">
  <div class="grid-bg"></div>
  <div class="hero-layout">
    <div>
      <div class="pill-row">
        <span class="badge badge-green">Minecraft 1.21.1</span>
        <span class="badge badge-blue">NeoForge</span>
        <span class="badge badge-amber">Server &amp; Client Ready</span>
      </div>
      <div class="hero-title">Universal<br/>Uncrafting Table</div>
      <p class="hero-sub">
        A smart, dynamic deconstruction workstation that safely breaks down vanilla &amp; modded items into their base ingredients — no tedious hardcoding required.
      </p>
      <div class="feat-grid">
        <div class="feat-item">
          <div class="feat-icon"><img class="px" src="{s('diamond_sword')}"/></div>
          <div><h3>4×4 Batch Grids</h3><p>16 input + 16 output slots prevent clogging</p></div>
        </div>
        <div class="feat-item">
          <div class="feat-icon"><img src="{s('cobbled_deepslate')}"/></div>
          <div><h3>Pipez-Style Recipe Filters</h3><p>Save item output rules for automation &amp; pipes</p></div>
        </div>
        <div class="feat-item">
          <div class="feat-icon"><img src="{s('grindstone')}"/></div>
          <div><h3>4 Station Modifiers</h3><p>Grindstone, Smoker, Blast Furnace &amp; Brewing</p></div>
        </div>
        <div class="feat-item">
          <div class="feat-icon"><img class="px" src="{s('ultimate_speed_card')}"/></div>
          <div><h3>FE Auto &amp; Multi-Mod</h3><p>Supports Powah, Pipez &amp; custom crafting</p></div>
        </div>
      </div>
    </div>

    <div class="stage">
      <div class="stage-ring"></div>
      <div class="flow-top">
        <div class="mc-slot"><img class="px" src="{s('netherite_sword_ench')}"/></div>
        <span style="font-size:24px;color:#94a3b8;font-weight:700;">+</span>
        <div class="mc-slot"><img class="px" src="{s('book')}"/></div>
        <span class="flow-arrow">&#10140;</span>
        <div class="mc-slot"><img class="px" src="{s('diamond_sword')}"/></div>
        <div class="mc-slot"><img class="px" src="{s('netherite_ingot')}"/></div>
        <div class="mc-slot"><img class="px" src="{s('netherite_upgrade_smithing_template')}"/></div>
        <div class="mc-slot"><img class="px" src="{s('enchanted_book_ench')}"/></div>
      </div>
      <img class="hero-table" src="{r('ut_hero')}"/>
      <div class="float-chip" style="left: 16px; bottom: 118px; border-color: rgba(248,113,113,0.5);">
        <img src="{s('powah_cell')}" />
        <span>10,000 FE Auto Buffer</span>
      </div>
      <div class="float-chip" style="right: 10px; bottom: 118px; border-color: rgba(96,165,250,0.5);">
        <img class="px" src="{s('hopper')}" />
        <span>Pipe &amp; Hopper Safe I/O</span>
      </div>
    </div>
  </div>
</div></body></html>"""


def html_gui_tour():
    # gui_accurate.png is (86 + 214)*4 x 203*4 = 1200 x 812.
    # Let's display it at width: 1060px, height: 717px centered at x=430..1490, y=200..917 ->Wait!
    # Left column cards: x=46..396 (w=350), Right column cards: x=1524..1874 (w=350), GUI centered at x=420..1480?
    # Let's compute exact pixel positions for clean orthogonal SVG leader lines!
    # Suppose GUI box is placed at left=420, top=215, width=1060, height=717.
    # Original gui_accurate.png size = 1200 x 812 (300 x 203 base units, scale = 1060/300 = 3.5333px per base unit!)
    # Base coordinates inside 300x203:
    #   Settings panel: x=2..86, y=5..56 -> center (44, 30) -> canvas (420 + 44*3.533, 215 + 30*3.533) = (575, 321)
    #   Settings cog: x=86+12=98, y=10 -> canvas (766, 250)
    #   Input 3x3: x=86+12..86+66 -> (98..152), y=20..74 -> left edge (420+98*3.533=766, y=380)
    #   Books slot: x=86+30..86+48 (116..134), y=88..106 -> center (125, 97) -> canvas (862, 558)
    #   Uncraft & Auto buttons: x=86+77..86+137 (163..223), y=20..65 -> bottom/center (193, 42) -> canvas (1102, 363)
    #   Speed slot: x=86+98..86+116 (184..202), y=88..106 -> bottom (193, 106) -> canvas (1102, 590)
    #   Output 3x3: x=86+148..86+202 (234..288), y=20..74 -> right edge (288, 47) -> canvas (1438, 381)
    #   FE bar: x=86+204..86+210 (290..296), y=20..74 -> right edge (296, 47) -> canvas (1466, 381) -> wait, use y=62 for FE bar (1466, 434) and y=34 for Output (1438, 335)!
    #   Modifiers + checkboxes: x=86+130..86+202 (216..288), y=88..114 -> right/bottom (288, 102) -> canvas (1438, 575)
    S = 1030.0 / 342.0  # 3.0117
    GX, GY = 445, 240
    GW, GH = 1030, int(round(228 * S))  # 687

    def pt(bx, by):
        return int(round(GX + bx * S)), int(round(GY + by * S))

    p_panel  = pt(2, 22)     # Clean left edge of Settings Panel
    p_input  = pt(94, 62)    # Input 4x4 left edge (cleanly below Settings Panel)
    p_books  = pt(102, 113)  # Books & Speed slots
    p_picker = pt(208, 82)   # Bottom of Recipes (3) button
    p_btns   = pt(173, 30)   # Uncraft/Auto left edge
    p_gap1_x = pt(157, 0)[0] # Center between "Input" (144) and "Uncrafting Table" (170)
    p_gap2_x = pt(169, 0)[0] # Center between Input 4x4 (166) and Uncraft button (173)
    p_gap_y  = pt(0, 18)[1]  # Clean channel above slots
    p_out    = pt(322, 21)   # Top-right corner of Output 4x4 (never crosses FE bar)
    p_fe     = pt(332, 68)   # FE bar right edge
    p_mods   = pt(322, 114)  # Modifiers right edge

    return f"""<!DOCTYPE html><html><head><meta charset="utf-8"><style>{COMMON_CSS}
.ann {{
  position: absolute; z-index: 4; width: 350px;
  background: rgba(18, 26, 36, 0.94);
  border: 1.5px solid rgba(116, 168, 129, 0.45);
  border-radius: 16px; padding: 16px 20px;
  box-shadow: 0 14px 32px rgba(0,0,0,0.45);
}}
.ann-head {{ display: flex; align-items: center; gap: 11px; margin-bottom: 6px; }}
.num {{
  width: 30px; height: 30px; border-radius: 50%;
  background: #2c633a; border: 1.5px solid #86efac;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 16px; font-weight: 800; color: #fff; flex-shrink: 0;
}}
.ann h3 {{ font-size: 20px; font-weight: 700; color: #f8fafc; }}
.ann p {{ font-size: 16px; line-height: 1.38; color: #a3b3c6; }}
.gui-frame {{
  position: absolute; left: {GX}px; top: {GY}px; width: {GW}px; height: {GH}px; z-index: 3;
  filter: drop-shadow(0 28px 50px rgba(0,0,0,0.75));
}}
.gui-frame img {{ width: 100%; height: 100%; image-rendering: pixelated; }}
</style></head><body>
<div class="canvas" style="padding: 46px 60px;">
  <div class="grid-bg"></div>
  <div class="topbar" style="margin-bottom: 12px;">
    <div class="title-group">
      <h1>Complete GUI &amp; Settings Guide</h1>
      <p>Expanded 4&times;4 grids, Pipez-style Recipes (N) button, slide-out settings, modifiers &amp; FE meter</p>
    </div>
    <div class="brand-pill"><img src="{r('ut_logo')}"/> Universal Uncrafting Table</div>
  </div>

  <!-- Left Annotations -->
  <div class="ann" style="left: 52px; top: 200px;">
    <div class="ann-head"><span class="num">1</span><h3>Slide-Out Settings Panel</h3></div>
    <p>Click the <b>&#9881; Gear</b> icon to toggle uncrafting sound effects &amp; particle effects anytime.</p>
  </div>
  <div class="ann" style="left: 52px; top: 405px;">
    <div class="ann-head"><span class="num">2</span><h3>4&times;4 Batch Input Grid</h3></div>
    <p>16 input slots prevent clogging. Green &amp; blue corner dots track saved recipe filters &amp; multi-recipe items.</p>
  </div>
  <div class="ann" style="left: 52px; top: 615px;">
    <div class="ann-head"><span class="num">3</span><h3>Books &amp; Speed Card Slots</h3></div>
    <p>Insert Books to extract enchantments, and Speed Cards for up to <b>1 item/tick</b> auto speed.</p>
  </div>
  <div class="ann" style="left: 52px; top: 830px; border-color: rgba(56, 189, 248, 0.55);">
    <div class="ann-head"><span class="num" style="background:#0369a1;border-color:#38bdf8;">4</span><h3>Recipes (N) Filters</h3></div>
    <p>Open the Pipez-style <b>Recipe Filters</b> list to pick exact output recipes &amp; Auto Default / Wait policy.</p>
  </div>

  <!-- Right Annotations -->
  <div class="ann" style="right: 52px; top: 195px; border-color: rgba(96, 165, 250, 0.55);">
    <div class="ann-head"><span class="num" style="background:#1e40af;border-color:#93c5fd;">5</span><h3>Manual &amp; Auto Controls</h3></div>
    <p><b>Uncraft</b> runs manually for <b>0 FE</b>. Toggle <b>Auto: ON</b> to process continuously with FE power.</p>
  </div>
  <div class="ann" style="right: 52px; top: 400px;">
    <div class="ann-head"><span class="num">6</span><h3>4&times;4 Batch Output Grid</h3></div>
    <p>16 output slots hold recovered materials, Enchanted Books &amp; safely rejected items without jamming.</p>
  </div>
  <div class="ann" style="right: 52px; top: 610px; border-color: rgba(248, 113, 113, 0.55);">
    <div class="ann-head"><span class="num" style="background:#991b1b;border-color:#fca5a5;">7</span><h3>10,000 FE Energy Buffer</h3></div>
    <p>Accepts Forge Energy from cables or energy cells (Powah, Pipez, etc.) to fuel Auto mode.</p>
  </div>
  <div class="ann" style="right: 52px; top: 825px;">
    <div class="ann-head"><span class="num">8</span><h3>4 Station Modifiers</h3></div>
    <p>Grindstone, Smoker, Blast Furnace &amp; Brewing Stand slots with per-modifier enable toggles below.</p>
  </div>

  <div class="gui-frame"><img src="{r('gui_accurate')}"/></div>

  <svg style="position:absolute;inset:0;width:1920px;height:1080px;z-index:5;pointer-events:none;" viewBox="0 0 1920 1080" fill="none">
    <!-- 1: Panel left edge -->
    <polyline points="402,260 426,260 426,{p_panel[1]} {p_panel[0]},{p_panel[1]}" stroke="#86efac" stroke-width="3"/>
    <circle cx="{p_panel[0]}" cy="{p_panel[1]}" r="6" fill="#86efac"/>
    <!-- 2: Input below Settings Panel -->
    <polyline points="402,465 710,465 710,{p_input[1]} {p_input[0]},{p_input[1]}" stroke="#86efac" stroke-width="3"/>
    <circle cx="{p_input[0]}" cy="{p_input[1]}" r="6" fill="#86efac"/>
    <!-- 3: Books & Speed -->
    <polyline points="402,675 732,675 732,{p_books[1]} {p_books[0]},{p_books[1]}" stroke="#86efac" stroke-width="3"/>
    <circle cx="{p_books[0]}" cy="{p_books[1]}" r="6" fill="#86efac"/>
    <!-- 4: Recipe & Material Picker (bottom) -->
    <polyline points="402,945 {p_picker[0]},945 {p_picker[0]},{p_picker[1]}" stroke="#38bdf8" stroke-width="3"/>
    <circle cx="{p_picker[0]}" cy="{p_picker[1]}" r="6" fill="#38bdf8"/>
    <!-- 5: Buttons routed cleanly OVER the GUI top edge & down between Input & Uncraft -->
    <polyline points="1518,220 {p_gap1_x},220 {p_gap1_x},{p_gap_y} {p_gap2_x},{p_gap_y} {p_gap2_x},{p_btns[1]} {p_btns[0]},{p_btns[1]}" stroke="#93c5fd" stroke-width="3"/>
    <circle cx="{p_btns[0]}" cy="{p_btns[1]}" r="6" fill="#93c5fd"/>
    <!-- 6: Output (routed above FE bar into top-right of Output grid) -->
    <polyline points="1518,445 1492,445 1492,{p_out[1]} {p_out[0]},{p_out[1]}" stroke="#86efac" stroke-width="3"/>
    <circle cx="{p_out[0]}" cy="{p_out[1]}" r="6" fill="#86efac"/>
    <!-- 7: FE Bar -->
    <polyline points="1518,665 1485,665 1485,{p_fe[1]} {p_fe[0]},{p_fe[1]}" stroke="#f87171" stroke-width="3"/>
    <circle cx="{p_fe[0]}" cy="{p_fe[1]}" r="6" fill="#f87171"/>
    <!-- 8: Modifiers -->
    <polyline points="1518,880 1475,880 1475,{p_mods[1]} {p_mods[0]},{p_mods[1]}" stroke="#86efac" stroke-width="3"/>
    <circle cx="{p_mods[0]}" cy="{p_mods[1]}" r="6" fill="#86efac"/>
  </svg>
</div></body></html>"""


def html_crafting():
    recipe = [
        ("iron_ingot", "Iron Ingot"), ("lapis_block", "Lapis Block"), ("iron_ingot", "Iron Ingot"),
        ("diamond", "Diamond"), ("crafting_table", "Crafting Table"), ("diamond", "Diamond"),
        ("obsidian", "Obsidian"), ("obsidian", "Obsidian"), ("obsidian", "Obsidian"),
    ]
    slots_html = ""
    for sp, title in recipe:
        cls = "px" if sp in ("iron_ingot", "diamond") else ""
        slots_html += f'<div class="craft-slot"><img class="{cls}" src="{s(sp)}" title="{title}"/></div>\n'

    return f"""<!DOCTYPE html><html><head><meta charset="utf-8"><style>{COMMON_CSS}
.two-col {{ position: relative; z-index: 2; flex: 1; display: grid; grid-template-columns: 1.05fr 0.95fr; gap: 38px; }}
.recipe-card {{ padding: 40px 44px; display: flex; flex-direction: column; justify-content: space-between; }}
.recipe-stage {{ display: flex; align-items: center; justify-content: space-between; gap: 22px; margin: 18px 0; }}
.craft-grid {{
  display: grid; grid-template-columns: repeat(3, 118px); grid-template-rows: repeat(3, 118px); gap: 10px;
  padding: 18px; border-radius: 18px;
  background: #141920; border: 2px solid rgba(148,163,184,0.22);
}}
.craft-slot {{
  width: 118px; height: 118px;
  background: #23272c;
  border-top: 4px solid #121417; border-left: 4px solid #121417;
  border-right: 4px solid #3c424a; border-bottom: 4px solid #3c424a;
  border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
}}
.craft-slot img {{ width: 88px; height: 88px; object-fit: contain; }}
.arrow-big {{ font-size: 62px; color: #4ade80; font-weight: 900; }}
.result-box {{
  width: 280px; height: 320px; border-radius: 22px;
  background: radial-gradient(circle at center, rgba(44,99,58,0.35), rgba(17,24,33,0.92));
  border: 2px solid rgba(134,239,172,0.5);
  display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 16px;
}}
.result-box img {{ width: 215px; height: 215px; object-fit: contain; filter: drop-shadow(0 18px 20px rgba(0,0,0,0.65)); }}
.result-box span {{ margin-top: 12px; font-size: 20px; font-weight: 800; color: #d8f3dc; text-align: center; }}
.ing-list {{
  display: flex; flex-wrap: wrap; gap: 12px;
  padding-top: 18px; border-top: 1px solid rgba(148,163,184,0.16);
}}
.ing-pill {{
  display: inline-flex; align-items: center; gap: 10px;
  padding: 8px 14px; border-radius: 12px;
  background: rgba(15, 22, 31, 0.85); border: 1px solid rgba(148,163,184,0.2);
  font-size: 17px; font-weight: 600; color: #cbd5e1;
}}
.ing-pill img {{ width: 28px; height: 28px; object-fit: contain; }}

.steps-col {{ display: flex; flex-direction: column; justify-content: space-between; gap: 22px; }}
.step-card {{ padding: 28px 32px; display: flex; align-items: flex-start; gap: 22px; flex: 1; }}
.step-num {{
  font-family: 'Bahnschrift', sans-serif;
  font-size: 42px; font-weight: 800; line-height: 1;
  color: #4ade80; background: rgba(44, 99, 58, 0.35);
  border: 1.5px solid rgba(134, 239, 172, 0.45);
  border-radius: 16px; width: 78px; height: 78px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}}
.step-card h3 {{ font-size: 27px; font-weight: 700; color: #f8fafc; margin-bottom: 8px; }}
.step-card p {{ font-size: 20px; line-height: 1.45; color: #a7b6c8; }}
</style></head><body>
<div class="canvas">
  <div class="grid-bg"></div>
  <div class="topbar">
    <div class="title-group">
      <h1>Crafting Recipe &amp; Quick Start</h1>
      <p>Easy survival progression into a universal recycling &amp; gear-recovery station</p>
    </div>
    <div class="brand-pill"><img src="{r('ut_logo')}"/> Universal Uncrafting Table</div>
  </div>

  <div class="two-col">
    <div class="card recipe-card">
      <div style="display:flex;align-items:center;justify-content:space-between;">
        <span class="badge badge-green">Shaped Crafting Recipe</span>
        <span style="font-size:18px;color:#94a3b8;font-weight:600;">3×3 Crafting Table</span>
      </div>
      <div class="recipe-stage">
        <div class="craft-grid">{slots_html}</div>
        <div class="arrow-big">&#10140;</div>
        <div class="result-box">
          <img src="{r('ut_hero')}"/>
          <span>Uncrafting Table &times;1</span>
        </div>
      </div>
      <div class="ing-list">
        <span class="ing-pill"><img class="px" src="{s('iron_ingot')}"/> 2&times; Iron Ingot</span>
        <span class="ing-pill"><img src="{s('lapis_block')}"/> 1&times; Lapis Block</span>
        <span class="ing-pill"><img class="px" src="{s('diamond')}"/> 2&times; Diamond</span>
        <span class="ing-pill"><img src="{s('crafting_table')}"/> 1&times; Crafting Table</span>
        <span class="ing-pill"><img src="{s('obsidian')}"/> 3&times; Obsidian</span>
      </div>
    </div>

    <div class="steps-col">
      <div class="card step-card">
        <div class="step-num">01</div>
        <div>
          <h3>Place &amp; Connect</h3>
          <p>Place the Uncrafting Table in your workshop. Use it standalone by hand, or connect Hoppers, Pipez &amp; Forge Energy cables.</p>
        </div>
      </div>
      <div class="card step-card">
        <div class="step-num">02</div>
        <div>
          <h3>Load Items, Books &amp; Modifiers</h3>
          <p>Drop vanilla or modded gear into the 4&times;4 Input Grid, set any custom recipe filters, and add Books or modifiers.</p>
        </div>
      </div>
      <div class="card step-card">
        <div class="step-num">03</div>
        <div>
          <h3>Uncraft Free or Automate with FE</h3>
          <p>Click <b>Uncraft</b> manually anytime for <b>0 FE</b>, or supply Forge Energy (e.g. Powah) and toggle <b>Auto: ON</b> for continuous processing.</p>
        </div>
      </div>
    </div>
  </div>
</div></body></html>"""


def html_modifiers():
    return f"""<!DOCTYPE html><html><head><meta charset="utf-8"><style>{COMMON_CSS}
.mod-grid {{
  position: relative; z-index: 2;
  display: grid; grid-template-columns: 1fr 1fr; gap: 24px;
}}
.mod-card {{
  padding: 24px 28px; display: flex; align-items: center; gap: 24px;
}}
.station-box {{
  width: 158px; height: 158px; border-radius: 18px;
  background: rgba(15, 22, 31, 0.9); border: 1.5px solid rgba(148,163,184,0.22);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}}
.station-box img {{ width: 132px; height: 132px; object-fit: contain; }}
.mod-body {{ flex: 1; }}
.mod-head {{ display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }}
.mod-head h3 {{ font-size: 26px; font-weight: 800; color: #fff; }}
.mod-desc {{ font-size: 17px; color: #9fb0c3; margin-bottom: 14px; line-height: 1.35; }}
.io-row {{ display: flex; align-items: center; gap: 10px; }}
.io-arrow {{ font-size: 28px; color: #4ade80; font-weight: 900; padding: 0 4px; }}
.io-plus {{ font-size: 24px; color: #94a3b8; font-weight: 800; }}
.smithing-bar {{
  position: relative; z-index: 2; margin-top: 24px;
  padding: 22px 32px;
  background: linear-gradient(90deg, rgba(88, 28, 135, 0.30) 0%, rgba(20, 29, 40, 0.95) 55%, rgba(16, 24, 34, 0.96) 100%);
  border: 1.5px solid rgba(192, 132, 252, 0.42);
  border-radius: 22px;
  display: flex; align-items: center; justify-content: space-between; gap: 24px;
}}
</style></head><body>
<div class="canvas" style="padding: 46px 64px;">
  <div class="grid-bg"></div>
  <div class="topbar" style="margin-bottom: 24px;">
    <div class="title-group">
      <h1>Station Modifiers, Enchantments &amp; Smithing</h1>
      <p>Insert station blocks into Modifier Slots (or toggle checkboxes) to unlock specialized deconstruction</p>
    </div>
    <div class="brand-pill"><img src="{r('ut_logo')}"/> Universal Uncrafting Table</div>
  </div>

  <div class="mod-grid">
    <div class="card mod-card">
      <div class="station-box"><img src="{r('grindstone_iso')}"/></div>
      <div class="mod-body">
        <div class="mod-head"><h3>Grindstone</h3><span class="badge badge-purple">Enchants</span></div>
        <p class="mod-desc">Uncraft enchanted gear and extract enchantments onto Books cleanly.</p>
        <div class="io-row">
          <div class="mc-slot"><img class="px" src="{s('iron_axe_ench')}"/></div>
          <span class="io-plus">+</span>
          <div class="mc-slot"><img class="px" src="{s('book')}"/></div>
          <span class="io-arrow">&#10140;</span>
          <div class="mc-slot"><img class="px" src="{s('iron_ingot')}"/><span class="cnt">3</span></div>
          <div class="mc-slot"><img class="px" src="{s('stick')}"/><span class="cnt">2</span></div>
          <div class="mc-slot"><img class="px" src="{s('enchanted_book_ench')}"/></div>
        </div>
      </div>
    </div>

    <div class="card mod-card">
      <div class="station-box"><img src="{r('smoker_iso')}"/></div>
      <div class="mod-body">
        <div class="mod-head"><h3>Smoker</h3><span class="badge badge-amber">Food</span></div>
        <p class="mod-desc">Reverse smoked, campfire-cooked, or furnace-cooked foods back to raw ingredients.</p>
        <div class="io-row">
          <div class="mc-slot"><img class="px" src="{s('cooked_porkchop')}"/><span class="cnt">8</span></div>
          <span class="io-arrow">&#10140;</span>
          <div class="mc-slot"><img class="px" src="{s('porkchop')}"/><span class="cnt">8</span></div>
        </div>
      </div>
    </div>

    <div class="card mod-card">
      <div class="station-box"><img src="{r('blast_furnace_iso')}"/></div>
      <div class="mod-body">
        <div class="mod-head"><h3>Blast Furnace</h3><span class="badge badge-red">Smelting</span></div>
        <p class="mod-desc">Uncraft blasted ingots and smelted metals back into their raw ore materials.</p>
        <div class="io-row">
          <div class="mc-slot"><img class="px" src="{s('gold_ingot')}"/><span class="cnt">4</span></div>
          <span class="io-arrow">&#10140;</span>
          <div class="mc-slot"><img class="px" src="{s('raw_gold')}"/><span class="cnt">4</span></div>
        </div>
      </div>
    </div>

    <div class="card mod-card">
      <div class="station-box"><img src="{r('brewing_stand_iso')}"/></div>
      <div class="mod-body">
        <div class="mod-head"><h3>Brewing Stand</h3><span class="badge badge-blue">3&times; Potions</span></div>
        <p class="mod-desc">Reverses Lingering &rarr; Splash &rarr; Potion &rarr; Water + reagent (3 identical potions in Input).</p>
        <div class="io-row">
          <div class="mc-slot"><img class="px" src="{s('lingering_healing')}"/></div>
          <div class="mc-slot"><img class="px" src="{s('lingering_healing')}"/></div>
          <div class="mc-slot"><img class="px" src="{s('lingering_healing')}"/></div>
          <span class="io-arrow">&#10140;</span>
          <div class="mc-slot"><img class="px" src="{s('splash_healing')}"/><span class="cnt">3</span></div>
          <div class="mc-slot"><img class="px" src="{s('dragon_breath')}"/></div>
        </div>
      </div>
    </div>
  </div>

  <div class="smithing-bar">
    <div>
      <span class="badge badge-purple" style="margin-bottom:8px;">Native Smithing Table Support</span>
      <h3 style="font-size:28px;font-weight:800;color:#fff;">Full Netherite &amp; Modded Smithing Recovery</h3>
      <p style="font-size:19px;color:#cbd5e1;margin-top:4px;">Break Netherite gear back into Diamond equipment, Netherite Ingots, Smithing Templates &amp; Books!</p>
    </div>
    <div class="io-row" style="flex-shrink:0;">
      <div class="mc-slot"><img class="px" src="{s('netherite_sword_ench')}"/></div>
      <span class="io-plus">+</span>
      <div class="mc-slot"><img class="px" src="{s('book')}"/></div>
      <span class="io-arrow">&#10140;</span>
      <div class="mc-slot"><img class="px" src="{s('diamond_sword')}"/></div>
      <div class="mc-slot"><img class="px" src="{s('netherite_ingot')}"/></div>
      <div class="mc-slot"><img class="px" src="{s('netherite_upgrade_smithing_template')}"/></div>
      <div class="mc-slot"><img class="px" src="{s('enchanted_book_ench')}"/></div>
    </div>
  </div>
</div></body></html>"""


def html_speed_and_fe():
    tiers = [
        ("Basic", "basic_speed_card", "20 ticks (1.0s)", "200 FE / action", "#94a3b8", "rgba(148,163,184,0.35)",
         ["redstone", "iron_ingot", "redstone", "iron_ingot", "paper", "iron_ingot", "redstone", "iron_ingot", "redstone"], "Shaped Craft"),
        ("Advanced", "advanced_speed_card", "10 ticks (0.5s)", "400 FE / action", "#fbbf24", "rgba(251,191,36,0.45)",
         ["redstone", "gold_ingot", "redstone", "gold_ingot", "basic_speed_card", "gold_ingot", "redstone", "gold_ingot", "redstone"], "Shaped Craft"),
        ("Elite", "elite_speed_card", "5 ticks (0.25s)", "600 FE / action", "#22d3ee", "rgba(34,211,238,0.45)",
         ["redstone", "diamond", "redstone", "diamond", "advanced_speed_card", "diamond", "redstone", "diamond", "redstone"], "Shaped Craft"),
        ("Ultimate", "ultimate_speed_card", "1 tick (20/sec!)", "800 FE / action", "#e879f9", "rgba(232,121,249,0.50)",
         ["netherite_upgrade_smithing_template", "elite_speed_card", "netherite_ingot"], "Smithing Upgrade"),
        ("Creative", "creative_speed_card", "1 tick (20/sec!)", "0 FE (Free!)", "#4ade80", "rgba(74,222,128,0.55)",
         [], "OP / Creative Tab"),
    ]

    cards_html = ""
    for name, sprite, spd, fe, accent, bcol, rec, rtype in tiers:
        if len(rec) == 9:
            r_html = '<div class="mini-grid">' + "".join(f'<div class="mini-cell"><img class="px" src="{s(x)}"/></div>' for x in rec) + '</div>'
        elif len(rec) == 3:
            r_html = '<div class="smith-row">' + "".join(f'<div class="mini-cell"><img class="px" src="{s(x)}"/></div>' for x in rec) + '</div>'
        else:
            r_html = '<div class="creative-box"><span>Operator / Creative<br/>Unlimited Energy</span></div>'

        cards_html += f"""
        <div class="card tier-card" style="border-color:{bcol};">
          <div class="tier-top">
            <img class="px tier-icon" src="{s(sprite)}"/>
            <h3 style="color:{accent};">{name}</h3>
            <span class="tier-sub">Speed Card</span>
          </div>
          <div class="stat-pill"><span>Interval</span><b>{spd}</b></div>
          <div class="stat-pill"><span>Auto Cost</span><b style="color:#fca5a5;">{fe}</b></div>
          <div class="recipe-foot">
            <span class="r-label">{rtype}</span>
            {r_html}
          </div>
        </div>"""

    return f"""<!DOCTYPE html><html><head><meta charset="utf-8"><style>{COMMON_CSS}
.fe-banner {{
  position: relative; z-index: 2;
  padding: 20px 30px; margin-bottom: 28px;
  border-radius: 20px;
  background: linear-gradient(90deg, rgba(220, 38, 38, 0.20) 0%, rgba(20, 29, 40, 0.94) 52%, rgba(245, 158, 11, 0.14) 100%);
  border: 1.5px solid rgba(248, 113, 113, 0.42);
  display: flex; align-items: center; justify-content: space-between; gap: 28px;
}}
.fe-left {{ display: flex; align-items: center; gap: 20px; }}
.fe-left img {{ width: 76px; height: 76px; object-fit: contain; }}
.fe-left h2 {{ font-size: 27px; font-weight: 800; color: #fff; }}
.fe-left p {{ font-size: 19px; color: #cbd5e1; margin-top: 4px; }}
.fe-compare {{ display: flex; gap: 14px; flex-shrink: 0; }}
.fe-box {{
  padding: 12px 20px; border-radius: 14px;
  background: rgba(12, 17, 23, 0.85); border: 1px solid rgba(148,163,184,0.24);
  text-align: center;
}}
.fe-box span {{ display: block; font-size: 14px; color: #94a3b8; text-transform: uppercase; font-weight: 700; }}
.fe-box b {{ font-size: 21px; color: #86efac; }}

.tier-row {{
  position: relative; z-index: 2; flex: 1;
  display: grid; grid-template-columns: repeat(5, 1fr); gap: 20px;
}}
.tier-card {{
  padding: 24px 20px; display: flex; flex-direction: column; align-items: center;
}}
.tier-top {{ text-align: center; margin-bottom: 16px; }}
.tier-icon {{ width: 84px; height: 84px; margin-bottom: 10px; filter: drop-shadow(0 8px 14px rgba(0,0,0,0.5)); }}
.tier-top h3 {{ font-size: 28px; font-weight: 800; }}
.tier-sub {{ font-size: 15px; color: #94a3b8; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; }}
.stat-pill {{
  width: 100%; padding: 10px 14px; border-radius: 12px;
  background: rgba(12, 17, 23, 0.78); border: 1px solid rgba(148,163,184,0.16);
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 10px; font-size: 16px;
}}
.stat-pill span {{ color: #94a3b8; font-weight: 600; }}
.stat-pill b {{ color: #f8fafc; font-weight: 700; }}
.recipe-foot {{
  margin-top: auto; width: 100%; padding-top: 14px;
  border-top: 1px solid rgba(148,163,184,0.16);
  display: flex; flex-direction: column; align-items: center;
}}
.r-label {{ font-size: 14px; font-weight: 700; color: #9fb0c3; text-transform: uppercase; margin-bottom: 10px; }}
.mini-grid {{ display: grid; grid-template-columns: repeat(3, 46px); gap: 4px; }}
.smith-row {{ display: flex; gap: 6px; margin: 46px 0; }}
.mini-cell {{
  width: 46px; height: 46px; background: #23272c;
  border: 2px solid #121417; border-radius: 5px;
  display: flex; align-items: center; justify-content: center;
}}
.mini-cell img {{ width: 34px; height: 34px; object-fit: contain; }}
.creative-box {{
  height: 146px; display: flex; align-items: center; justify-content: center; text-align: center;
  color: #86efac; font-size: 17px; font-weight: 700; line-height: 1.45;
  padding: 0 12px;
}}
</style></head><body>
<div class="canvas" style="padding: 44px 58px;">
  <div class="grid-bg"></div>
  <div class="topbar" style="margin-bottom: 20px;">
    <div class="title-group">
      <h1>Speed Cards &amp; Forge Energy (FE)</h1>
      <p>Scale from manual crafting-bench use to 20 items/second automated industrial recycling</p>
    </div>
    <div class="brand-pill"><img src="{r('ut_logo')}"/> Universal Uncrafting Table</div>
  </div>

  <div class="fe-banner">
    <div class="fe-left">
      <img src="{s('powah_cell')}"/>
      <div>
        <h2>What is FE (Forge Energy) For?</h2>
        <p><b>Manual Uncraft</b> is always free. Connect any FE power mod (like <b>Powah</b>, Mekanism, or Pipez) to power <b>Auto: ON</b> mode!</p>
      </div>
    </div>
    <div class="fe-compare">
      <div class="fe-box"><span>Manual Button</span><b>0 FE (Free)</b></div>
      <div class="fe-box"><span>Default Auto (No Card)</span><b style="color:#fca5a5;">100 FE / 40t</b></div>
      <div class="fe-box"><span>Internal Buffer</span><b style="color:#93c5fd;">10,000 FE</b></div>
    </div>
  </div>

  <div class="tier-row">{cards_html}</div>
</div></body></html>"""


def html_automation():
    return f"""<!DOCTYPE html><html><head><meta charset="utf-8"><style>{COMMON_CSS}
.auto-layout {{
  position: relative; z-index: 2; flex: 1;
  display: grid; grid-template-columns: 1.12fr 0.88fr; gap: 36px; align-items: stretch;
}}
.diorama-card {{
  position: relative; padding: 20px;
  display: flex; align-items: center; justify-content: center;
  background: radial-gradient(circle at 50% 48%, rgba(44, 99, 58, 0.24) 0%, rgba(15, 22, 31, 0.95) 75%);
  overflow: hidden;
}}
.diorama-img {{
  width: 960px; height: 720px; object-fit: contain;
  filter: drop-shadow(0 28px 34px rgba(0,0,0,0.68));
}}
.dio-tag {{
  position: absolute; padding: 9px 16px; border-radius: 12px;
  background: rgba(12, 17, 23, 0.92);
  border: 1.5px solid rgba(148,163,184,0.34);
  font-size: 16px; font-weight: 700; color: #f8fafc;
  box-shadow: 0 10px 24px rgba(0,0,0,0.45);
}}
.info-col {{ display: flex; flex-direction: column; gap: 22px; justify-content: space-between; }}
.info-card {{ padding: 28px 30px; flex: 1; display: flex; flex-direction: column; justify-content: center; }}
.info-card h3 {{ font-size: 26px; font-weight: 800; color: #fff; margin: 10px 0 8px; }}
.info-card p {{ font-size: 19px; line-height: 1.45; color: #a7b6c8; }}
.tag-list {{ display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }}
.mini-tag {{
  padding: 5px 12px; border-radius: 8px; font-size: 15px; font-weight: 600;
  background: rgba(12,17,23,0.8); border: 1px solid rgba(148,163,184,0.22); color: #cbd5e1;
}}
</style></head><body>
<div class="canvas">
  <div class="grid-bg"></div>
  <div class="topbar">
    <div class="title-group">
      <h1>Automation, Pipes &amp; Mod Compatibility</h1>
      <p>Plug into Hoppers, Pipez &amp; Powah networks with smart recipe resolution and exploit safety</p>
    </div>
    <div class="brand-pill"><img src="{r('ut_logo')}"/> Universal Uncrafting Table</div>
  </div>

  <div class="auto-layout">
    <div class="card diorama-card">
      <img class="diorama-img" src="{r('automation_diorama')}"/>
      <div class="dio-tag" style="left: 28px; top: 34px; border-color: rgba(251, 191, 36, 0.55);">
        &#9660; Hopper + Barrel + Pipez Input
      </div>
      <div class="dio-tag" style="right: 28px; top: 88px; border-color: rgba(248, 113, 113, 0.6);">
        &#9889; Powah Energy Cell &amp; Cable (FE)
      </div>
      <div class="dio-tag" style="left: 28px; bottom: 28px; border-color: rgba(134, 239, 172, 0.55);">
        &#9654; Auto-Extracted Outputs (Barrel)
      </div>
    </div>

    <div class="info-col">
      <div class="card info-card">
        <div><span class="badge badge-amber">Hoppers &amp; Pipez Ready</span></div>
        <h3>Protected Automated I/O</h3>
        <p>External hoppers and pipes automatically feed <b>Input</b> &amp; <b>Books</b> and only pull finished items from <b>Output</b> — modifier blocks stay locked safely in place.</p>
      </div>
      <div class="card info-card">
        <div><span class="badge badge-green">Multi-Mod Crafting Support</span></div>
        <h3>Universal &amp; Custom Crafting Engine</h3>
        <p>Resolves Crafting, Smithing, Stonecutting, Blasting, Smoking, Brewing, custom <b>SizedIngredient</b> recipes &amp; <b>Iron's Gems 'n Jewelry</b> data dynamically.</p>
      </div>
      <div class="card info-card">
        <div><span class="badge badge-blue">Safe &amp; Configurable</span></div>
        <h3>Anti-Exploit &amp; Datapack Blacklist</h3>
        <p>Rejects infinite-loop recipes and cleanly routes blacklisted items to Output without deleting gear.</p>
        <div class="tag-list">
          <span class="mini-tag">Buckets &amp; Bottles</span>
          <span class="mini-tag">Blood Magic Orbs</span>
          <span class="mini-tag">AE2 Knives</span>
          <span class="mini-tag">Create Glue</span>
          <span class="mini-tag">#uncrafting_table:blacklist</span>
        </div>
      </div>
    </div>
  </div>
</div></body></html>"""


def html_recipe_filters():
    return f"""<!DOCTYPE html><html><head><meta charset="utf-8"><style>{COMMON_CSS}
.filter-cols {{
  position: relative; z-index: 2; flex: 1;
  display: grid; grid-template-columns: 1fr 1fr; gap: 34px; align-items: stretch;
}}
.win-card {{
  padding: 24px 30px; display: flex; flex-direction: column; justify-content: space-between;
}}
.win-head {{
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;
}}
.win-head h2 {{
  font-size: 27px; font-weight: 800; color: #fff;
}}
.win-img-wrap {{
  display: flex; align-items: center; justify-content: center;
  padding: 6px 0;
}}
.win-img-wrap img {{
  width: 650px; height: 579px; image-rendering: pixelated;
  filter: drop-shadow(0 20px 36px rgba(0, 0, 0, 0.72));
}}
.win-notes {{
  display: grid; grid-template-columns: 1fr 1fr; gap: 14px;
}}
.win-note {{
  padding: 13px 16px; border-radius: 14px;
  background: rgba(12, 17, 23, 0.85); border: 1px solid rgba(148, 163, 184, 0.22);
}}
.win-note > b {{
  display: block; font-size: 17px; color: #f8fafc; margin-bottom: 4px;
}}
.win-note span {{
  font-size: 15px; line-height: 1.38; color: #9fb0c3;
}}
.win-note span b {{
  color: #e2e8f0; font-weight: 700;
}}
</style></head><body>
<div class="canvas" style="padding: 44px 62px;">
  <div class="grid-bg"></div>
  <div class="topbar" style="margin-bottom: 22px;">
    <div class="title-group">
      <h1>Pipez-Style Output Recipe Filters</h1>
      <p>Configure exact multi-recipe outputs upfront so Hoppers, Pipez &amp; Storage Networks stay in full control</p>
    </div>
    <div class="brand-pill"><img src="{r('ut_logo')}"/> Universal Uncrafting Table</div>
  </div>

  <div class="filter-cols">
    <div class="card win-card" style="border-color: rgba(56, 189, 248, 0.42);">
      <div class="win-head">
        <h2>1. Saved Recipe Filters List</h2>
        <span class="badge badge-blue">Recipes (N) Button</span>
      </div>
      <div class="win-img-wrap">
        <img src="{r('gui_filter_list')}"/>
      </div>
      <div class="win-notes">
        <div class="win-note">
          <b>Manage Persistent Rules</b>
          <span>Click <b>Add</b>, <b>Edit</b> (or double-click), or <b>Remove</b> rules anytime. Rules persist inside the table.</span>
        </div>
        <div class="win-note">
          <b>Auto-Mode Multi-Recipe Policy</b>
          <span>Toggle between <b>Auto Default</b> and <b>Wait for Filter</b> to pause unlisted items piped from storage.</span>
        </div>
      </div>
    </div>

    <div class="card win-card" style="border-color: rgba(74, 222, 128, 0.42);">
      <div class="win-head">
        <h2>2. Drag-Item Configure Window</h2>
        <span class="badge badge-green">Ghost Slot + JEI Drag</span>
      </div>
      <div class="win-img-wrap">
        <img src="{r('gui_filter_edit')}"/>
      </div>
      <div class="win-notes">
        <div class="win-note">
          <b>Drop Any Target Item</b>
          <span>Click with cursor item, <b>Shift+Click</b> your live inventory below, or drag directly from <b>JEI</b>.</span>
        </div>
        <div class="win-note">
          <b>Pick Concrete Outputs</b>
          <span>Choose stone, planks, or alternate recipes (and inline tag swatches) then click <b>Save</b>.</span>
        </div>
      </div>
    </div>
  </div>
</div></body></html>"""


PAGES = [
    ("00_logo.png", (800, 800), html_logo),
    ("01_hero_showcase.png", (1920, 1080), html_hero),
    ("02_gui_tour.png", (1920, 1080), html_gui_tour),
    ("03_crafting_recipe.png", (1920, 1080), html_crafting),
    ("04_modifiers_and_recovery.png", (1920, 1080), html_modifiers),
    ("05_speed_cards_and_fe.png", (1920, 1080), html_speed_and_fe),
    ("06_automation_and_mods.png", (1920, 1080), html_automation),
    ("07_recipe_filters.png", (1920, 1080), html_recipe_filters),
]


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch()
        for fname, (w, h), fn in PAGES:
            page = browser.new_page(viewport={"width": w, "height": h}, device_scale_factor=1)
            page.set_content(fn(), wait_until="load")
            out_path = os.path.join(OUT_DIR, fname)
            page.screenshot(path=out_path, type="png")
            page.close()
            print(f"Generated {out_path} ({w}x{h})")
        browser.close()


if __name__ == "__main__":
    main()
