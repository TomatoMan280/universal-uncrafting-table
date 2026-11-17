import json
import os
import numpy as np
from PIL import Image, ImageDraw

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MC_TEX_DIR = os.path.join(BASE_DIR, "textures", "mc")
MOD_ROOT = os.path.dirname(BASE_DIR)
MOD_TEX_DIR = os.path.join(MOD_ROOT, "src", "main", "resources", "assets", "uncrafting_table", "textures")
RENDERS_DIR = os.path.join(BASE_DIR, "renders")
SPRITES_DIR = os.path.join(BASE_DIR, "sprites")


def tint_image(img, rgb):
    arr = np.array(img.convert("RGBA"), dtype=np.float32)
    arr[..., 0] *= rgb[0] / 255.0
    arr[..., 1] *= rgb[1] / 255.0
    arr[..., 2] *= rgb[2] / 255.0
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")


def add_enchant_glint(img):
    glint_path = os.path.join(MC_TEX_DIR, "enchanted_glint_item.png")
    glint = Image.open(glint_path).convert("RGBA").resize(img.size, Image.Resampling.BILINEAR)
    base = np.array(img.convert("RGBA"), dtype=np.float32)
    g = np.array(glint, dtype=np.float32)
    mask = (base[..., 3:4] > 20).astype(np.float32)
    # Screen/additive purple tint like MC item glint
    purple = np.zeros_like(base[..., :3])
    purple[..., 0] = g[..., 0] * 0.65 + 75.0
    purple[..., 1] = g[..., 1] * 0.25 + 15.0
    purple[..., 2] = g[..., 2] * 0.95 + 115.0
    out = base.copy()
    out[..., :3] = np.clip(base[..., :3] + purple * 0.34 * mask, 0, 255)
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def make_potion_sprite(kind="potion", color=(248, 36, 110)):
    overlay = Image.open(os.path.join(MC_TEX_DIR, "potion_overlay.png")).convert("RGBA")
    bottle = Image.open(os.path.join(MC_TEX_DIR, f"{kind}.png")).convert("RGBA")
    tinted = tint_image(overlay, color)
    out = Image.alpha_composite(tinted, bottle)
    return out


def prepare_all_sprites():
    os.makedirs(SPRITES_DIR, exist_ok=True)
    os.makedirs(RENDERS_DIR, exist_ok=True)

    # Render missing cube icons (lapis_block, obsidian, oak_planks, spruce_planks, cobblestone, cobbled_deepslate, blackstone)
    from mc_renderer import make_cube_model, extract_quads_from_model, render_quads
    cube_specs = {
        "lapis_block_iso": "block/lapis_block",
        "obsidian_iso": "block/obsidian",
        "spruce_planks_iso": "block/spruce_planks",
        "oak_planks_iso": "block/oak_planks",
        "cobblestone_iso": "block/cobblestone",
        "cobbled_deepslate_iso": "block/cobbled_deepslate",
        "blackstone_iso": "block/blackstone",
    }
    for out_name, tex in cube_specs.items():
        m = make_cube_model(tex, tex, tex, tex, tex, tex)
        render_quads(extract_quads_from_model(m), out_size=(256, 256), yaw_deg=45.0, pitch_deg=30.0).save(
            os.path.join(RENDERS_DIR, f"{out_name}.png")
        )

    # Upscale 16x16 items to crisp 128x128 nearest-neighbor sprites
    items = [
        "iron_ingot", "gold_ingot", "diamond", "netherite_ingot",
        "netherite_upgrade_smithing_template", "netherite_sword", "diamond_sword",
        "iron_axe", "stone_pickaxe", "stick", "book", "enchanted_book", "redstone", "paper",
        "cooked_porkchop", "porkchop", "raw_gold", "gunpowder", "dragon_breath",
        "nether_wart", "glass_bottle", "hopper"
    ]
    for name in items:
        im = Image.open(os.path.join(MC_TEX_DIR, f"{name}.png")).convert("RGBA")
        im.resize((128, 128), Image.Resampling.NEAREST).save(os.path.join(SPRITES_DIR, f"{name}.png"))

    # Enchanted versions
    for name in ["netherite_sword", "iron_axe", "enchanted_book"]:
        im = Image.open(os.path.join(MC_TEX_DIR, f"{name}.png")).convert("RGBA")
        im_e = add_enchant_glint(im)
        im_e.resize((128, 128), Image.Resampling.NEAREST).save(os.path.join(SPRITES_DIR, f"{name}_ench.png"))

    # Potions (Healing pink-red & Awkward blue/water)
    make_potion_sprite("potion", (248, 36, 108)).resize((128, 128), Image.Resampling.NEAREST).save(os.path.join(SPRITES_DIR, "potion_healing.png"))
    make_potion_sprite("splash_potion", (248, 36, 108)).resize((128, 128), Image.Resampling.NEAREST).save(os.path.join(SPRITES_DIR, "splash_healing.png"))
    make_potion_sprite("lingering_potion", (248, 36, 108)).resize((128, 128), Image.Resampling.NEAREST).save(os.path.join(SPRITES_DIR, "lingering_healing.png"))
    make_potion_sprite("potion", (60, 110, 240)).resize((128, 128), Image.Resampling.NEAREST).save(os.path.join(SPRITES_DIR, "potion_water.png"))

    # Mod speed cards
    for card in ["basic_speed_card", "advanced_speed_card", "elite_speed_card", "ultimate_speed_card", "creative_speed_card"]:
        im = Image.open(os.path.join(MOD_TEX_DIR, "item", f"{card}.png")).convert("RGBA")
        im.resize((128, 128), Image.Resampling.NEAREST).save(os.path.join(SPRITES_DIR, f"{card}.png"))

    # Block icons scaled nicely to 128x128 for slots
    for iso in [
        "ut_logo", "crafting_table_iso", "lapis_block_iso", "obsidian_iso",
        "spruce_planks_iso", "oak_planks_iso", "cobblestone_iso", "cobbled_deepslate_iso", "blackstone_iso",
        "grindstone_iso", "smoker_iso", "blast_furnace_iso", "brewing_stand_iso", "powah_cell_iso"
    ]:
        im = Image.open(os.path.join(RENDERS_DIR, f"{iso}.png")).convert("RGBA")
        im.resize((128, 128), Image.Resampling.LANCZOS).save(os.path.join(SPRITES_DIR, f"{iso.replace('_iso', '')}.png"))


class McBitmapFont:
    def __init__(self, ascii_path):
        self.sheet = Image.open(ascii_path).convert("RGBA")
        self.cell_w = self.sheet.width // 16
        self.cell_h = self.sheet.height // 16
        self.glyphs = {}
        self.widths = {}
        for code in range(256):
            gx = (code % 16) * self.cell_w
            gy = (code // 16) * self.cell_h
            cell = self.sheet.crop((gx, gy, gx + self.cell_w, gy + self.cell_h))
            self.glyphs[code] = cell
            if code == 32:
                self.widths[code] = 4
            else:
                arr = np.array(cell)
                cols = np.where(arr[..., 3].max(axis=0) > 10)[0]
                self.widths[code] = int(cols.max()) + 2 if len(cols) > 0 else 4

    def width(self, text):
        return sum(self.widths.get(ord(ch), 5) for ch in text) - (1 if text else 0)

    def draw(self, canvas, text, x, y, color=(255, 255, 255), shadow=False):
        cx = int(round(x))
        cy = int(round(y))
        for ch in text:
            code = ord(ch)
            if code in self.glyphs and code != 32:
                g = self.glyphs[code]
                if shadow:
                    sh_col = (color[0] // 4, color[1] // 4, color[2] // 4)
                    canvas.paste(tint_image(g, sh_col), (cx + 1, cy + 1), g)
                canvas.paste(tint_image(g, color), (cx, cy), g)
            cx += self.widths.get(code, 5)


def draw_mc_button(canvas, btn_img, x, y, w, h, text, font, text_color=(255, 255, 255)):
    # 9-slice / stretch MC button sprite (button.png is 200x20 in 1.21 sprites, or scaled cleanly)
    bw, bh = btn_img.size
    # Use 3px border 9-slice so corners stay sharp
    b = 3
    tl = btn_img.crop((0, 0, b, b))
    tr = btn_img.crop((bw - b, 0, bw, b))
    bl = btn_img.crop((0, bh - b, b, bh))
    br = btn_img.crop((bw - b, bh - b, bw, bh))
    top = btn_img.crop((b, 0, bw - b, b)).resize((w - 2 * b, b), Image.Resampling.NEAREST)
    bot = btn_img.crop((b, bh - b, bw - b, bh)).resize((w - 2 * b, b), Image.Resampling.NEAREST)
    left = btn_img.crop((0, b, b, bh - b)).resize((b, h - 2 * b), Image.Resampling.NEAREST)
    right = btn_img.crop((bw - b, b, bw, bh - b)).resize((b, h - 2 * b), Image.Resampling.NEAREST)
    mid = btn_img.crop((b, b, bw - b, bh - b)).resize((w - 2 * b, h - 2 * b), Image.Resampling.NEAREST)

    canvas.paste(mid, (x + b, y + b), mid)
    canvas.paste(top, (x + b, y), top)
    canvas.paste(bot, (x + b, y + h - b), bot)
    canvas.paste(left, (x, y + b), left)
    canvas.paste(right, (x + w - b, y + b), right)
    canvas.paste(tl, (x, y), tl)
    canvas.paste(tr, (x + w - b, y), tr)
    canvas.paste(bl, (x, y + h - b), bl)
    canvas.paste(br, (x + w - b, y + h - b), br)

    tw = font.width(text)
    tx = x + (w - tw) // 2
    ty = y + (h - 8) // 2
    font.draw(canvas, text, tx, ty, color=text_color, shadow=True)


def render_accurate_gui(out_path, scale=4):
    """
    Render the exact 4x4 UncraftingTableScreen (with Recipe/Material Picker & Slide-out Settings panel) at 4x crisp scale.
    Base coords match UncraftingTableScreen.java & UncraftingTableMenu.java pixel-for-pixel.
    """
    gui_tex = Image.open(os.path.join(MOD_TEX_DIR, "gui", "container", "uncrafting_table.png")).convert("RGBA")
    btn_norm = Image.open(os.path.join(MC_TEX_DIR, "button.png")).convert("RGBA")
    btn_hov = Image.open(os.path.join(MC_TEX_DIR, "button_highlighted.png")).convert("RGBA")
    font = McBitmapFont(os.path.join(MC_TEX_DIR, "ascii.png"))

    # Shift right by 86px so the slide-out Settings panel (-84..0) fits cleanly inside the canvas!
    ox = 86
    total_w = ox + 256
    total_h = 228
    canvas = Image.new("RGBA", (total_w, total_h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    # Main GUI background (256x228)
    main_bg = gui_tex.crop((0, 0, 256, 228))
    canvas.paste(main_bg, (ox, 0), main_bg)

    # Slide-out Settings Panel
    px, py, pw, ph = ox - 84, 5, 84, 51
    draw.rectangle([px, py, px + pw - 1, py + ph - 1], fill=(0, 0, 0, 255))
    draw.rectangle([px + 1, py + 1, px + pw - 1, py + ph - 2], fill=(35, 39, 42, 255))
    draw.line([(px + 1, py + 1), (px + pw - 2, py + 1)], fill=(50, 53, 58, 255))
    draw.line([(px + 1, py + 1), (px + 1, py + ph - 2)], fill=(50, 53, 58, 255))
    draw.line([(px + 1, py + ph - 2), (px + pw - 2, py + ph - 2)], fill=(20, 22, 24, 255))
    draw.line([(px + pw - 2, py + 1), (px + pw - 2, py + ph - 2)], fill=(20, 22, 24, 255))

    # Panel buttons
    draw_mc_button(canvas, btn_norm, px + 2, 10, 78, 16, "Sounds: ON", font)
    draw_mc_button(canvas, btn_norm, px + 2, 30, 78, 16, "Particles: ON", font)

    # Main buttons: Uncraft, Auto: ON & Recipes (3) (70x18 / 70x20 at x=87)
    draw_mc_button(canvas, btn_hov, ox + 87, 20, 70, 18, "Uncraft", font, text_color=(255, 255, 160))
    draw_mc_button(canvas, btn_norm, ox + 87, 40, 70, 18, "Auto: ON", font, text_color=(140, 255, 140))
    draw_mc_button(canvas, btn_norm, ox + 87, 62, 70, 20, "Recipes (3)", font, text_color=(255, 255, 255))

    # Recessed status box below Recipes (3) (x=87, y=86, w=70, h=42)
    draw.rectangle([ox + 87, 86, ox + 156, 127], fill=(24, 25, 28, 255))
    draw.line([(ox + 87, 86), (ox + 156, 86)], fill=(15, 16, 18, 255))
    draw.line([(ox + 87, 86), (ox + 87, 127)], fill=(15, 16, 18, 255))
    draw.line([(ox + 87, 127), (ox + 156, 127)], fill=(45, 48, 52, 255))
    draw.line([(ox + 156, 86), (ox + 156, 127)], fill=(45, 48, 52, 255))

    # Cog gear icon at (7, 5)
    gear_pts = [
        (3,1),(4,1),(6,1),(7,1),
        (2,2),(4,2),(5,2),(6,2),(8,2),
        (1,3),(2,3),(3,3),(7,3),(8,3),(9,3),
        (2,4),(3,4),(7,4),(8,4),
        (1,5),(2,5),(8,5),(9,5),
        (2,6),(3,6),(7,6),(8,6),
        (1,7),(2,7),(3,7),(7,7),(8,7),(9,7),
        (2,8),(4,8),(5,8),(6,8),(8,8),
        (3,9),(4,9),(6,9),(7,9),
    ]
    for gx, gy in gear_pts:
        canvas.putpixel((ox + 7 + gx, 5 + gy), (255, 215, 0, 255))

    # 4 Modifier checkboxes (x=164+i*18+4, y=123)
    for i in range(4):
        cx, cy = ox + 164 + i * 18 + 4, 123
        draw.rectangle([cx, cy, cx + 7, cy + 7], fill=(34, 34, 34, 255))
        draw.rectangle([cx + 1, cy + 1, cx + 6, cy + 6], fill=(82, 86, 92, 255))

    # Energy Bar (x=240, y=20, w=6, h=72)
    bx, by, bw, bh = ox + 240, 20, 6, 72
    draw.rectangle([bx, by, bx + bw - 1, by + bh - 1], fill=(204, 0, 0, 255))
    draw.rectangle([bx, by, bx + 1, by + bh - 1], fill=(238, 45, 45, 255))

    # Labels
    title = "Uncrafting Table"
    font.draw(canvas, title, ox + (256 - font.width(title)) // 2, 6, color=(255, 255, 255))
    font.draw(canvas, "Inventory", ox + 47, 135, color=(255, 255, 255))
    font.draw(canvas, "Input", ox + 44 - font.width("Input") // 2, 11, color=(170, 170, 170))
    font.draw(canvas, "Output", ox + 200 - font.width("Output") // 2, 11, color=(170, 170, 170))
    font.draw(canvas, "Books", ox + 24 - font.width("Books") // 2, 95, color=(170, 170, 170))
    font.draw(canvas, "Speed", ox + 60 - font.width("Speed") // 2, 95, color=(170, 170, 170))

    big = canvas.resize((total_w * scale, total_h * scale), Image.Resampling.NEAREST)

    # Input 4x4 (x=8+c*18, y=20+r*18): Stone Pickaxe (has saved rule) + Netherite Sword + Cooked Porkchop + Gold Ingot (multi-recipe) + Iron Axe + 3x Potions
    _place_item(big, font, scale, "stone_pickaxe", ox + 8, 20)
    _place_item(big, font, scale, "netherite_sword_ench", ox + 26, 20)
    _place_item(big, font, scale, "cooked_porkchop", ox + 44, 20, 8)
    _place_item(big, font, scale, "gold_ingot", ox + 62, 20, 4)
    _place_item(big, font, scale, "iron_axe_ench", ox + 8, 38)
    _place_item(big, font, scale, "lingering_healing", ox + 8, 56)
    _place_item(big, font, scale, "lingering_healing", ox + 26, 56)
    _place_item(big, font, scale, "lingering_healing", ox + 44, 56)

    # Corner status dots on Input grid (Green = saved rule, Cyan = multi-recipe using Auto Default)
    big_draw = ImageDraw.Draw(big)
    for col, row, dot_col in ((0, 0, (74, 222, 128, 255)), (3, 0, (56, 189, 248, 255))):
        dx = (ox + 8 + col * 18 + 13) * scale
        dy = (20 + row * 18) * scale
        big_draw.rectangle([dx, dy, dx + 3 * scale - 1, dy + 3 * scale - 1], fill=dot_col)

    # Output 4x4 (x=164+c*18, y=20+r*18)
    _place_item(big, font, scale, "cobbled_deepslate", ox + 164, 20, 3)
    _place_item(big, font, scale, "stick", ox + 182, 20, 4)
    _place_item(big, font, scale, "diamond_sword", ox + 200, 20)
    _place_item(big, font, scale, "netherite_ingot", ox + 218, 20)
    _place_item(big, font, scale, "netherite_upgrade_smithing_template", ox + 164, 38)
    _place_item(big, font, scale, "enchanted_book_ench", ox + 182, 38)
    _place_item(big, font, scale, "porkchop", ox + 200, 38, 8)
    _place_item(big, font, scale, "raw_gold", ox + 218, 38, 4)
    _place_item(big, font, scale, "iron_ingot", ox + 164, 56, 3)

    # Book (16, 105) & Speed (52, 105)
    _place_item(big, font, scale, "book", ox + 16, 105, 16)
    _place_item(big, font, scale, "ultimate_speed_card", ox + 52, 105)

    # 4 Modifiers (164, 182, 200, 218; y=105)
    _place_item(big, font, scale, "grindstone", ox + 164, 105)
    _place_item(big, font, scale, "smoker", ox + 182, 105)
    _place_item(big, font, scale, "blast_furnace", ox + 200, 105)
    _place_item(big, font, scale, "brewing_stand", ox + 218, 105)

    # Hotbar sample items (47 + c*18, 203)
    _place_item(big, font, scale, "basic_speed_card", ox + 47, 203)
    _place_item(big, font, scale, "advanced_speed_card", ox + 65, 203)
    _place_item(big, font, scale, "elite_speed_card", ox + 83, 203)
    _place_item(big, font, scale, "spruce_planks", ox + 173, 203, 8)

    # Status box micro lines inside recessed box below Recipes (3)
    _draw_micro_text(big, font, scale, "Unset: Default", ox + 122, 92, 0.62, (159, 176, 195), centered=True)
    _draw_micro_text(big, font, scale, "1 multi in input", ox + 122, 104, 0.62, (134, 239, 172), centered=True)
    _draw_micro_text(big, font, scale, "Pipes follow rules", ox + 122, 116, 0.62, (107, 114, 128), centered=True)

    # Energy text ("10000 / 10000 FE") at x=203, y=95
    _draw_micro_text(big, font, scale, "10000 / 10000 FE", ox + 203, 95, 0.55, (255, 68, 68), centered=True)

    big.save(out_path)
    return out_path


def _place_item(big, font, scale, sprite_name, slot_x, slot_y, count=None):
    sp = Image.open(os.path.join(SPRITES_DIR, f"{sprite_name}.png")).convert("RGBA")
    is_iso = any(k in sprite_name for k in (
        "grindstone", "smoker", "blast_furnace", "brewing_stand",
        "planks", "cobble", "deepslate", "blackstone", "crafting_table"
    ))
    sp_resized = sp.resize((16 * scale, 16 * scale), Image.Resampling.LANCZOS if is_iso else Image.Resampling.NEAREST)
    big.paste(sp_resized, (slot_x * scale, slot_y * scale), sp_resized)
    if count and count > 1:
        txt = str(count)
        cnt_scale = max(1, scale - 1)
        tx = (slot_x + 17) * scale - font.width(txt) * cnt_scale - 2
        ty = (slot_y + 9) * scale
        c_layer = Image.new("RGBA", (60, 20), (0, 0, 0, 0))
        font.draw(c_layer, txt, 0, 0, color=(255, 255, 255), shadow=True)
        c_layer = c_layer.resize((60 * cnt_scale, 20 * cnt_scale), Image.Resampling.NEAREST)
        big.paste(c_layer, (tx, ty), c_layer)


def _draw_micro_text(big, font, scale, text, x, y, factor, color, centered=False, right_align=False, shadow=False):
    tw = font.width(text)
    layer = Image.new("RGBA", (tw + 4, 10), (0, 0, 0, 0))
    font.draw(layer, text, 0, 0, color=color, shadow=shadow)
    m_scale = max(1, int(round(scale * factor)))
    layer = layer.resize((layer.width * m_scale, layer.height * m_scale), Image.Resampling.NEAREST)
    if centered:
        px = int(x * scale - (tw * m_scale) / 2)
    elif right_align:
        px = int(x * scale - tw * m_scale)
    else:
        px = int(x * scale)
    big.paste(layer, (px, int(y * scale)), layer)


def _draw_panel_frame(draw, px, py, pw, ph):
    draw.rectangle([px, py, px + pw - 1, py + ph - 1], fill=(35, 39, 42, 255))
    draw.rectangle([px, py, px + pw - 1, py + 1], fill=(50, 53, 58, 255))
    draw.rectangle([px, py, px + 1, py + ph - 1], fill=(50, 53, 58, 255))
    draw.rectangle([px, py + ph - 2, px + pw - 1, py + ph - 1], fill=(20, 22, 24, 255))
    draw.rectangle([px + pw - 2, py, px + pw - 1, py + ph - 1], fill=(20, 22, 24, 255))


def _draw_recessed_box(draw, x, y, w, h):
    draw.rectangle([x, y, x + w - 1, y + h - 1], fill=(24, 25, 28, 255))
    draw.line([(x, y), (x + w - 1, y)], fill=(15, 16, 18, 255))
    draw.line([(x, y), (x, y + h - 1)], fill=(15, 16, 18, 255))
    draw.line([(x, y + h - 1), (x + w - 1, y + h - 1)], fill=(45, 48, 52, 255))
    draw.line([(x + w - 1, y), (x + w - 1, y + h - 1)], fill=(45, 48, 52, 255))


def render_filter_list_gui(out_path, scale=3):
    """Render Window 1: Pipez-style Recipe Filters list view (FILTER_LIST)."""
    btn_norm = Image.open(os.path.join(MC_TEX_DIR, "button.png")).convert("RGBA")
    btn_hov = Image.open(os.path.join(MC_TEX_DIR, "button_highlighted.png")).convert("RGBA")
    font = McBitmapFont(os.path.join(MC_TEX_DIR, "ascii.png"))

    canvas = Image.new("RGBA", (256, 228), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    _draw_panel_frame(draw, 0, 0, 256, 228)

    font.draw(canvas, "Recipe Filters", (256 - font.width("Recipe Filters")) // 2, 9, color=(255, 255, 255))
    draw_mc_button(canvas, btn_norm, 8, 5, 44, 16, "< Back", font)
    draw_mc_button(canvas, btn_norm, 192, 28, 56, 20, "Add", font)
    draw_mc_button(canvas, btn_hov, 192, 52, 56, 20, "Edit", font, text_color=(255, 255, 160))
    draw_mc_button(canvas, btn_norm, 192, 76, 56, 20, "Remove", font)
    draw_mc_button(canvas, btn_norm, 8, 199, 240, 20, "Unset Multi-Recipe: Auto Default", font)

    lx, ly, lw, lh = 8, 28, 178, 5 * 32 + 4
    _draw_recessed_box(draw, lx, ly, lw, lh)

    rules = [
        ("stone_pickaxe", "Stone Pickaxe", [("cobbled_deepslate", 3), ("stick", 2)], True),
        ("netherite_sword_ench", "Netherite Sword", [("diamond_sword", 1), ("netherite_ingot", 1), ("netherite_upgrade_smithing_template", 1)], False),
        ("cooked_porkchop", "Cooked Porkchop", [("porkchop", 1)], False),
    ]
    for i, (_, _, _, selected) in enumerate(rules):
        rx, ry, rw = lx + 2, ly + 2 + i * 32, lw - 8
        border = (56, 189, 248, 255) if selected else (42, 46, 51, 255)
        bg = (40, 58, 74, 255) if selected else (30, 33, 37, 255)
        draw.rectangle([rx, ry, rx + rw - 1, ry + 30], fill=border)
        draw.rectangle([rx + 1, ry + 1, rx + rw - 2, ry + 29], fill=bg)
        font.draw(canvas, "->", rx + 23, ry + 14, color=(74, 222, 128))

    big = canvas.resize((256 * scale, 228 * scale), Image.Resampling.NEAREST)
    for i, (target_sp, title, outs, _) in enumerate(rules):
        rx, ry = lx + 2, ly + 2 + i * 32
        _place_item(big, font, scale, target_sp, rx + 4, ry + 7)
        _draw_micro_text(big, font, scale, title, rx + 24, ry + 3, 0.75, (255, 255, 255))
        for o, (out_sp, cnt) in enumerate(outs):
            _place_item(big, font, scale, out_sp, rx + 36 + o * 18, ry + 12, cnt)

    big.save(out_path)
    return out_path


def render_filter_edit_gui(out_path, scale=3):
    """Render Window 2: Drag-item Configure Recipe view (FILTER_EDIT)."""
    gui_tex = Image.open(os.path.join(MOD_TEX_DIR, "gui", "container", "uncrafting_table.png")).convert("RGBA")
    btn_norm = Image.open(os.path.join(MC_TEX_DIR, "button.png")).convert("RGBA")
    btn_hov = Image.open(os.path.join(MC_TEX_DIR, "button_highlighted.png")).convert("RGBA")
    font = McBitmapFont(os.path.join(MC_TEX_DIR, "ascii.png"))

    canvas = Image.new("RGBA", (256, 228), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    _draw_panel_frame(draw, 0, 0, 256, 136)
    inv_strip = gui_tex.crop((0, 133, 256, 228))
    canvas.paste(inv_strip, (0, 133), inv_strip)

    font.draw(canvas, "Configure Recipe", (256 - font.width("Configure Recipe")) // 2, 9, color=(255, 255, 255))
    font.draw(canvas, "Inventory", 47, 135, color=(255, 255, 255))
    draw_mc_button(canvas, btn_norm, 8, 5, 48, 16, "Cancel", font)
    draw_mc_button(canvas, btn_hov, 200, 5, 48, 16, "Save", font, text_color=(255, 255, 160))

    gx, gy = 10, 26
    _draw_recessed_box(draw, gx, gy, 18, 18)
    font.draw(canvas, "Stone Pickaxe", gx + 24, gy + 2, color=(255, 255, 255))

    ox_box, oy_box, ow_box, oh_box = 10, 48, 236, 4 * 20 + 4
    _draw_recessed_box(draw, ox_box, oy_box, ow_box, oh_box)

    options = [
        ([("cobblestone", 3), ("stick", 2)], "Crafting", False),
        ([("cobbled_deepslate", 3), ("stick", 2)], "Crafting", True),
        ([("blackstone", 3), ("stick", 2)], "Crafting", False),
    ]
    for i, (_, _, selected) in enumerate(options):
        rx, ry, rw = ox_box + 2, oy_box + 2 + i * 20, ow_box - 8
        border = (74, 222, 128, 255) if selected else (40, 44, 50, 255)
        bg = (29, 51, 36, 255) if selected else (27, 30, 34, 255)
        draw.rectangle([rx, ry, rx + rw - 1, ry + 18], fill=border)
        draw.rectangle([rx + 1, ry + 1, rx + rw - 2, ry + 17], fill=bg)

    big = canvas.resize((256 * scale, 228 * scale), Image.Resampling.NEAREST)
    _place_item(big, font, scale, "stone_pickaxe", gx + 1, gy + 1)
    _draw_micro_text(big, font, scale, "3 output options found - pick one below:", gx + 24, gy + 12, 0.70, (134, 239, 172))

    for i, (stacks, src, selected) in enumerate(options):
        rx, ry, rw = ox_box + 2, oy_box + 2 + i * 20, ow_box - 8
        for k, (sp, cnt) in enumerate(stacks):
            _place_item(big, font, scale, sp, rx + 4 + k * 20, ry + 1, cnt)
        _draw_micro_text(
            big, font, scale, src, rx + rw - 4, ry + 6, 0.70,
            (134, 239, 172) if selected else (156, 163, 175), right_align=True
        )

    # Sample player inventory & hotbar items
    _place_item(big, font, scale, "stone_pickaxe", 47, 145)
    _place_item(big, font, scale, "netherite_sword_ench", 65, 145)
    _place_item(big, font, scale, "cooked_porkchop", 83, 145, 8)
    _place_item(big, font, scale, "oak_planks", 47, 203, 16)
    _place_item(big, font, scale, "spruce_planks", 65, 203, 16)
    _place_item(big, font, scale, "basic_speed_card", 83, 203)

    big.save(out_path)
    return out_path


if __name__ == "__main__":
    prepare_all_sprites()
    render_accurate_gui(os.path.join(RENDERS_DIR, "gui_accurate.png"), scale=4)
    render_filter_list_gui(os.path.join(RENDERS_DIR, "gui_filter_list.png"), scale=3)
    render_filter_edit_gui(os.path.join(RENDERS_DIR, "gui_filter_edit.png"), scale=3)
    print("Sprites & accurate GUIs generated!")
