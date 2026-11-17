import json
import math
import os
import numpy as np
from PIL import Image, ImageEnhance

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MC_TEX_DIR = os.path.join(BASE_DIR, "textures", "mc")
POWAH_TEX_DIR = os.path.join(BASE_DIR, "textures", "powah")
PIPEZ_TEX_DIR = os.path.join(BASE_DIR, "textures", "pipez")
MOD_ROOT = os.path.dirname(BASE_DIR)
MOD_TEX_DIR = os.path.join(MOD_ROOT, "src", "main", "resources", "assets", "uncrafting_table", "textures")


def load_texture(tex_ref):
    """Resolve a Minecraft/mod/Powah/Pipez texture path to an RGBA numpy uint8 array."""
    if tex_ref.startswith("#"):
        raise ValueError(f"Unresolved texture variable: {tex_ref}")
    if ":" in tex_ref:
        ns, rel = tex_ref.split(":", 1)
    else:
        ns, rel = "minecraft", tex_ref

    name = os.path.basename(rel)
    if not name.endswith(".png"):
        name += ".png"

    candidates = []
    if ns == "uncrafting_table":
        candidates.append(os.path.join(MOD_TEX_DIR, rel + ("" if rel.endswith(".png") else ".png")))
        candidates.append(os.path.join(MOD_TEX_DIR, "block", name))
    elif ns == "powah":
        candidates.append(os.path.join(POWAH_TEX_DIR, name))
    elif ns == "pipez":
        candidates.append(os.path.join(PIPEZ_TEX_DIR, name))
    else:
        candidates.append(os.path.join(MC_TEX_DIR, name))

    for c in candidates:
        if os.path.exists(c):
            im = Image.open(c).convert("RGBA")
            if im.height > im.width and im.height % im.width == 0:
                im = im.crop((0, 0, im.width, im.width))
            return np.array(im, dtype=np.uint8)
    raise FileNotFoundError(f"Texture not found for {tex_ref} (tried {candidates})")


def face_vertices_and_uvs(box_from, box_to, face_name, face_def):
    x1, y1, z1 = box_from
    x2, y2, z2 = box_to

    if face_name == "up":
        verts = [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)]
        default_uv = [x1, z1, x2, z2]
        shade = 1.00
    elif face_name == "down":
        verts = [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)]
        default_uv = [x1, 16 - z2, x2, 16 - z1]
        shade = 0.52
    elif face_name == "north":  # -Z
        verts = [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)]
        default_uv = [16 - x2, 16 - y2, 16 - x1, 16 - y1]
        shade = 0.86
    elif face_name == "south":  # +Z
        verts = [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)]
        default_uv = [x1, 16 - y2, x2, 16 - y1]
        shade = 0.82
    elif face_name == "east":  # +X
        verts = [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)]
        default_uv = [16 - z2, 16 - y2, 16 - z1, 16 - y1]
        shade = 0.68
    elif face_name == "west":  # -X
        verts = [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)]
        default_uv = [z1, 16 - y2, z2, 16 - y1]
        shade = 0.72
    else:
        raise ValueError(f"Unknown face {face_name}")

    uv = face_def.get("uv", default_uv)
    u0, v0, u1, v1 = uv
    uv_pts = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
    rot = face_def.get("rotation", 0)
    if rot:
        steps = (rot // 90) % 4
        uv_pts = uv_pts[-steps:] + uv_pts[:-steps]
    return verts, uv_pts, shade


def build_camera_basis(yaw_deg=35.0, pitch_deg=26.0):
    yaw = math.radians(yaw_deg)
    pitch = math.radians(pitch_deg)
    fx = -math.sin(yaw) * math.cos(pitch)
    fy = -math.sin(pitch)
    fz = math.cos(yaw) * math.cos(pitch)
    forward = np.array([fx, fy, fz], dtype=np.float64)
    forward /= np.linalg.norm(forward)

    world_up = np.array([0.0, 1.0, 0.0], dtype=np.float64)
    right = np.cross(forward, world_up)
    right /= np.linalg.norm(right)
    up = np.cross(right, forward)
    up /= np.linalg.norm(up)
    return right, up, forward


def resolve_tex_var(tex_map, ref):
    seen = set()
    cur = ref
    while cur.startswith("#"):
        key = cur[1:]
        if key in seen or key not in tex_map:
            return None
        seen.add(key)
        cur = tex_map[key]
    return cur


def rotate_point_euler(p, origin, axis, angle_deg):
    if not angle_deg:
        return p
    ox, oy, oz = origin
    x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
    rad = math.radians(angle_deg)
    c, s = math.cos(rad), math.sin(rad)
    if axis == "x":
        ny = y * c - z * s
        nz = y * s + z * c
        return (x + ox, ny + oy, nz + oz)
    elif axis == "y":
        nx = x * c + z * s
        nz = -x * s + z * c
        return (nx + ox, y + oy, nz + oz)
    elif axis == "z":
        nx = x * c - y * s
        ny = x * s + y * c
        return (nx + ox, ny + oy, z + oz)
    return p


def extract_quads_from_model(model_dict, tex_overrides=None, offset=(0.0, 0.0, 0.0), rot_y_90=0, rot_x_90=0, extra_shade=1.0, double_sided=False):
    tex_map = dict(model_dict.get("textures", {}))
    if tex_overrides:
        tex_map.update(tex_overrides)

    ox, oy, oz = offset
    quads = []

    def rot_pt(p):
        x, y, z = p[0] - 8.0, p[1] - 8.0, p[2] - 8.0
        for _ in range(rot_x_90 % 4):
            y, z = -z, y
        for _ in range(rot_y_90 % 4):
            x, z = -z, x
        return (x + 8.0 + ox, y + 8.0 + oy, z + 8.0 + oz)

    face_cycle = ["north", "east", "south", "west"]

    for elem in model_dict.get("elements", []):
        bfrom = elem["from"]
        bto = elem["to"]
        erot = elem.get("rotation")
        for fname, fdef in elem.get("faces", {}).items():
            tref = resolve_tex_var(tex_map, fdef["texture"])
            if not tref:
                continue
            tex = load_texture(tref)
            verts, uvs, shade = face_vertices_and_uvs(bfrom, bto, fname, fdef)
            if erot:
                verts = [rotate_point_euler(v, erot["origin"], erot["axis"], erot.get("angle", 0)) for v in verts]
            if elem.get("shade", True) is False:
                shade = max(shade, 0.94)
            if rot_y_90 % 4 != 0 and fname in face_cycle:
                new_fname = face_cycle[(face_cycle.index(fname) + rot_y_90) % 4]
                shade = {"north": 0.86, "south": 0.82, "east": 0.68, "west": 0.72}[new_fname]
            rverts = [rot_pt(v) for v in verts]
            quads.append((rverts, uvs, tex, shade * extra_shade, double_sided))
    return quads


def make_cube_model(top, bottom, north, south, east, west, from_pt=(0, 0, 0), to_pt=(16, 16, 16)):
    return {
        "textures": {
            "up": top, "down": bottom,
            "north": north, "south": south,
            "east": east, "west": west,
        },
        "elements": [{
            "from": list(from_pt),
            "to": list(to_pt),
            "faces": {
                "up": {"uv": [from_pt[0], from_pt[2], to_pt[0], to_pt[2]], "texture": "#up"},
                "down": {"uv": [from_pt[0], 16 - to_pt[2], to_pt[0], 16 - from_pt[2]], "texture": "#down"},
                "north": {"uv": [16 - to_pt[0], 16 - to_pt[1], 16 - from_pt[0], 16 - from_pt[1]], "texture": "#north"},
                "south": {"uv": [from_pt[0], 16 - to_pt[1], to_pt[0], 16 - from_pt[1]], "texture": "#south"},
                "east": {"uv": [16 - to_pt[2], 16 - to_pt[1], 16 - from_pt[2], 16 - from_pt[1]], "texture": "#east"},
                "west": {"uv": [from_pt[2], 16 - to_pt[1], to_pt[2], 16 - from_pt[1]], "texture": "#west"},
            }
        }]
    }


def render_quads(quads, out_size=(800, 800), yaw_deg=35.0, pitch_deg=26.0, ss=2, pad_frac=0.08):
    right, up, forward = build_camera_basis(yaw_deg, pitch_deg)

    all_pts = np.array([v for q in quads for v in q[0]], dtype=np.float64)
    px = all_pts @ right
    py = -(all_pts @ up)
    min_x, max_x = px.min(), px.max()
    min_y, max_y = py.min(), py.max()

    W, H = out_size[0] * ss, out_size[1] * ss
    span_x = max(max_x - min_x, 1e-6)
    span_y = max(max_y - min_y, 1e-6)
    scale = min(W * (1.0 - 2 * pad_frac) / span_x, H * (1.0 - 2 * pad_frac) / span_y)
    cx = (min_x + max_x) * 0.5
    cy = (min_y + max_y) * 0.5
    off_x = W * 0.5 - cx * scale
    off_y = H * 0.5 - cy * scale

    color_buf = np.zeros((H, W, 4), dtype=np.uint8)
    z_buf = np.full((H, W), np.inf, dtype=np.float64)

    for q in quads:
        verts, uvs, tex, shade = q[0], q[1], q[2], q[3]
        dbl = q[4] if len(q) > 4 else False
        pts3 = np.array(verts, dtype=np.float64)
        e1 = pts3[1] - pts3[0]
        e2 = pts3[2] - pts3[0]
        normal = np.cross(e1, e2)
        if not dbl and np.dot(normal, forward) <= 0:
            continue

        sx = pts3 @ right * scale + off_x
        sy = -(pts3 @ up) * scale + off_y
        sz = pts3 @ forward

        th, tw = tex.shape[0], tex.shape[1]
        for tri in ((0, 1, 2), (0, 2, 3)):
            i0, i1, i2 = tri
            x0, y0, z0 = sx[i0], sy[i0], sz[i0]
            x1, y1, z1 = sx[i1], sy[i1], sz[i1]
            x2, y2, z2 = sx[i2], sy[i2], sz[i2]

            bx0 = max(0, int(math.floor(min(x0, x1, x2))))
            bx1 = min(W - 1, int(math.ceil(max(x0, x1, x2))))
            by0 = max(0, int(math.floor(min(y0, y1, y2))))
            by1 = min(H - 1, int(math.ceil(max(y0, y1, y2))))
            if bx1 < bx0 or by1 < by0:
                continue

            denom = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
            if abs(denom) < 1e-8:
                continue

            xs = np.arange(bx0, bx1 + 1, dtype=np.float64) + 0.5
            ys = np.arange(by0, by1 + 1, dtype=np.float64) + 0.5
            gx, gy = np.meshgrid(xs, ys)

            w0 = ((y1 - y2) * (gx - x2) + (x2 - x1) * (gy - y2)) / denom
            w1 = ((y2 - y0) * (gx - x2) + (x0 - x2) * (gy - y2)) / denom
            w2 = 1.0 - w0 - w1

            inside = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
            if not np.any(inside):
                continue

            z_vals = w0 * z0 + w1 * z1 + w2 * z2
            sub_z = z_buf[by0:by1 + 1, bx0:bx1 + 1]
            z_pass = inside & (z_vals < sub_z)
            if not np.any(z_pass):
                continue

            u_vals = w0 * uvs[i0][0] + w1 * uvs[i1][0] + w2 * uvs[i2][0]
            v_vals = w0 * uvs[i0][1] + w1 * uvs[i1][1] + w2 * uvs[i2][1]

            iu = np.clip(np.floor(u_vals * (tw / 16.0)).astype(np.int32), 0, tw - 1)
            iv = np.clip(np.floor(v_vals * (th / 16.0)).astype(np.int32), 0, th - 1)

            sampled = tex[iv, iu]
            alpha_pass = z_pass & (sampled[..., 3] > 20)
            if not np.any(alpha_pass):
                continue

            shaded = sampled.copy()
            shaded[..., :3] = np.clip(shaded[..., :3].astype(np.float64) * shade, 0, 255).astype(np.uint8)

            sub_c = color_buf[by0:by1 + 1, bx0:bx1 + 1]
            sub_c[alpha_pass] = shaded[alpha_pass]
            sub_z[alpha_pass] = z_vals[alpha_pass]

    img = Image.fromarray(color_buf, "RGBA")
    if ss > 1:
        img = img.resize(out_size, Image.Resampling.LANCZOS)
    return img


def get_ut_model():
    with open(os.path.join(MOD_ROOT, "src/main/resources/assets/uncrafting_table/models/block/uncrafting_table.json")) as f:
        return json.load(f)


def build_powah_cable_arm(direction):
    """Build a Powah cable arm cuboid extending from center [6.25..9.75] toward direction."""
    if direction == "west":   # -X: 0..6.25
        f, t = [0, 6.25, 6.25], [6.25, 9.75, 9.75]
    elif direction == "east": # +X: 9.75..16
        f, t = [9.75, 6.25, 6.25], [16, 9.75, 9.75]
    elif direction == "north": # -Z: 0..6.25
        f, t = [6.25, 6.25, 0], [9.75, 9.75, 6.25]
    else:                      # +Z: 9.75..16
        f, t = [6.25, 6.25, 9.75], [9.75, 9.75, 16]
    return {
        "textures": {"cable": "powah:energy_cable_nitro"},
        "elements": [{
            "from": f, "to": t, "shade": False,
            "faces": {
                "north": {"uv": [0, 4, 6, 8], "texture": "#cable"},
                "south": {"uv": [0, 4, 6, 8], "texture": "#cable"},
                "east": {"uv": [0, 4, 6, 8], "texture": "#cable"},
                "west": {"uv": [0, 4, 6, 8], "texture": "#cable"},
                "up": {"uv": [0, 4, 6, 8], "texture": "#cable"},
                "down": {"uv": [0, 4, 6, 8], "texture": "#cable"},
            }
        }]
    }


def render_all_3d_assets():
    out_dir = os.path.join(BASE_DIR, "renders")
    os.makedirs(out_dir, exist_ok=True)

    ut = get_ut_model()
    # Best angle showing both green mat & vise prominently on front-right edge:
    # Let's test yaw=35 (vise front-left), yaw=55 (vise front-right/center), yaw=125 (east front-left, north front-right)
    # Earlier yaw=45 had +X_screen=-0.71, +Z_screen=-0.71 -> North (-Z) is front-right, East (+X) is front-left?
    # Wait: in test_ut_45.png, the handle (on +X) pointed down-left! That means +X is front-left, -Z is front-right!
    # Shifting yaw to 62 deg puts East (+X) more sideways to the left, letting the green cutting mat breathe!
    render_uncrafting_table_custom(os.path.join(out_dir, "ut_hero.png"), ut, size=(960, 960), yaw_deg=60.0, pitch_deg=26.0)
    render_uncrafting_table_custom(os.path.join(out_dir, "ut_logo.png"), ut, size=(720, 720), yaw_deg=45.0, pitch_deg=29.0)

    # Crafting table
    ct = make_cube_model("block/crafting_table_top", "block/oak_planks", "block/crafting_table_front", "block/crafting_table_side", "block/crafting_table_side", "block/crafting_table_front")
    render_quads(extract_quads_from_model(ct), out_size=(400, 400), yaw_deg=45.0, pitch_deg=30.0).save(os.path.join(out_dir, "crafting_table_iso.png"))

    # 4 Modifiers
    with open(os.path.join(MC_TEX_DIR, "grindstone_model.json")) as f:
        gs = json.load(f)
    render_quads(extract_quads_from_model(gs), out_size=(360, 360), yaw_deg=55.0, pitch_deg=26.0).save(os.path.join(out_dir, "grindstone_iso.png"))

    smoker = make_cube_model("block/smoker_top", "block/smoker_top", "block/smoker_front_on", "block/smoker_side", "block/smoker_side", "block/smoker_side")
    render_quads(extract_quads_from_model(smoker), out_size=(360, 360), yaw_deg=45.0, pitch_deg=28.0).save(os.path.join(out_dir, "smoker_iso.png"))

    blast = make_cube_model("block/blast_furnace_top", "block/blast_furnace_top", "block/blast_furnace_front_on", "block/blast_furnace_side", "block/blast_furnace_side", "block/blast_furnace_side")
    render_quads(extract_quads_from_model(blast), out_size=(360, 360), yaw_deg=45.0, pitch_deg=28.0).save(os.path.join(out_dir, "blast_furnace_iso.png"))

    with open(os.path.join(MC_TEX_DIR, "brewing_stand_model.json")) as f:
        bs = json.load(f)
    # Fix rod top cap UV & height so it isn't transparent in brewing_stand.png
    bs["elements"][0]["to"] = [9, 13, 9]
    bs["elements"][0]["faces"]["up"]["uv"] = [7, 3, 9, 5]
    for f in ("north", "south", "east", "west"):
        bs["elements"][0]["faces"][f]["uv"] = [7, 3, 9, 16]
    bs["elements"].extend([
        {
            "from": [8, 0, 8], "to": [16, 16, 8],
            "faces": {
                "north": {"uv": [0, 0, 8, 16], "texture": "#stand"},
                "south": {"uv": [8, 0, 0, 16], "texture": "#stand"}
            }
        },
        {
            "from": [-0.41, 0, 8], "to": [7.59, 16, 8],
            "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45},
            "faces": {
                "north": {"uv": [8, 0, 0, 16], "texture": "#stand"},
                "south": {"uv": [0, 0, 8, 16], "texture": "#stand"}
            }
        },
        {
            "from": [-0.41, 0, 8], "to": [7.59, 16, 8],
            "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": -45},
            "faces": {
                "north": {"uv": [8, 0, 0, 16], "texture": "#stand"},
                "south": {"uv": [0, 0, 8, 16], "texture": "#stand"}
            }
        }
    ])
    render_quads(extract_quads_from_model(bs, double_sided=True), out_size=(360, 360), yaw_deg=45.0, pitch_deg=26.0).save(os.path.join(out_dir, "brewing_stand_iso.png"))

    # Powah Energy Cell Nitro
    with open(os.path.join(POWAH_TEX_DIR, "energy_cell_model.json")) as f:
        ec = json.load(f)
    ec_quads = extract_quads_from_model(ec, tex_overrides={"0": "powah:energy_cell_nitro"})
    ec_core = make_cube_model("powah:energy_cell_core", "powah:energy_cell_core", "powah:energy_cell_core", "powah:energy_cell_core", "powah:energy_cell_core", "powah:energy_cell_core", (2, 2, 2), (14, 14, 14))
    ec_quads += extract_quads_from_model(ec_core, extra_shade=1.08)
    render_quads(ec_quads, out_size=(400, 400), yaw_deg=45.0, pitch_deg=28.0).save(os.path.join(out_dir, "powah_cell_iso.png"))

    # Automation Workshop Diorama!
    # Camera at yaw=42, pitch=27 looks from (+X, +Y, -Z):
    #   +X points front-left, -Z points front-right, -X points back-right, +Z points back-left!
    # So we place:
    #   Floor slab (5x5 at y=-6..0 -> shifted down by 16 so table legs sit at y=0!):
    #     x in [-2..2], z in [-2..2], slab from y=-6 to y=0
    #   Center (0, 0, 0): Uncrafting Table!
    #   Above Table (0, 16, 0): Hopper feeding DOWN into Uncrafting Table
    #   Input Barrel at (-16, 16, 0)? Wait: put Pipez Item Pipe at (0, 16, -16)? No, even cleaner:
    #     Input side (back-right, -X): Pipez Item Pipe at (-16, 0, 0) connecting (-32, 0, 0) Input Barrel -> Table!
    #     Top (0, 16, 0): Hopper on top of Table + Input Barrel at (0, 32, 0)? Wait, Hopper + Barrel on top looks tall and blocks nothing! Or put Hopper at (0, 16, 0) with Barrel at (-16, 16, 0) + Pipez Item Pipe at (-16, 0, 0)?
    #     Actually, let's do the super iconic automated setup:
    #       1) Uncrafting Table at (0, 16, 0) (elevated 1 block above floor so output Hopper + Barrel sit cleanly underneath? No, if Table is at y=0, it sits cleanly on the oak workshop floor!)
    #       Let's place Table at (0, 0, 0) on the oak floor!
    #       2) Top Hopper at (0, 12, 0)? Wait: Hopper is full block height 16 -> on top of table at (0, 16, 0)! Wait, table top is y=12..16? No, table_top is y=9..12! Putting Hopper at (0, 16, 0) leaves a 4px gap unless offset=(0, 12, 0) OR we feed via Pipez Item Pipe on the side!
    #       Side feeding is MUCH cleaner visually so the green cutting mat and vise stay visible! Look:
    #         - Center (0, 0, 0): Uncrafting Table (rot_y_90=0, vise on +X/-Z front!)
    #         - Back-left (+Z = -16? Wait: +Z points back-left!): Pipez Item Pipe at (0, 0, +16) connecting (0, 0, +32) Input Barrel -> Table!
    #         - Back-right (-X): Powah Nitro Energy Cable at (-16, 0, 0) connecting (-32, 0, 0) Powah Nitro Energy Cell -> Table!
    #         - Front-left (+X)? Wait: if we put Output Barrel at (+16, 0, 0) -> no, vise handle sticks out to x=17 on +X!
    #         - Put Powah Cell on back-right (-X: cable at (-16,0,0), cell at (-32,0,0)), AND put Pipez Item Pipe on back-left (+Z: pipe at (0,0,+16), barrel at (0,0,+32)) AND an Output Pipez Pipe + Output Barrel on (-16, 0, +16)? Even simpler & super clear:
    #           * Back-left (+Z): Input Barrel at (0, 0, 32) -> Pipez Item Pipe at (0, 0, 16) -> Uncrafting Table (0, 0, 0)
    #           * Back-right (-X): Powah Nitro Energy Cell at (-32, 0, 0) -> Powah Energy Cable at (-16, 0, 0) -> Uncrafting Table (0, 0, 0)
    #           * Front-right (-Z)? No, keep front completely open! Put Output Hopper + Output Barrel at (+16, 0, 16) connected by Pipez Item Pipe at (+16, 0, 0) -> wait: (+16, 0, 0) is +X (front-left), which is right beside the table without blocking the front view!
    #           * Wait: let's check if Hopper sits on top of Output Barrel! Yes!
    #             If Output Barrel is at (+32, 0, 0), Hopper or Pipez Pipe at (+16, 0, 0) connects Uncrafting Table -> Output Barrel! Even better: Pipez Extract Pipe from Table at (+16, 0, 0) into Output Barrel at (+32, 0, 0)! That shows ALL THREE: Pipez Item Extraction, Pipez/Barrel Input, AND Powah FE Energy Cell + Cable!
    diorama_quads = []
    floor_oak = make_cube_model("block/oak_planks", "block/oak_planks", "block/oak_planks", "block/oak_planks", "block/oak_planks", "block/oak_planks", (0, 10, 0), (16, 16, 16))
    floor_slate = make_cube_model("block/deepslate_bricks", "block/deepslate_bricks", "block/deepslate_bricks", "block/deepslate_bricks", "block/deepslate_bricks", "block/deepslate_bricks", (0, 10, 0), (16, 16, 16))
    barrel = make_cube_model("block/barrel_top", "block/barrel_bottom", "block/barrel_side", "block/barrel_side", "block/barrel_side", "block/barrel_side")

    # 5x5 floor centered at (0,0), shifted slightly so (0, 0..2) and (+2, +1) are supported cleanly
    for bx in range(-2, 3):
        for bz in range(-1, 3):
            is_inner = (-1 <= bx <= 1 and 0 <= bz <= 1)
            m = floor_oak if is_inner else floor_slate
            diorama_quads.extend(extract_quads_from_model(m, offset=(bx * 16, -16, bz * 16), extra_shade=0.95))

    # Center: Uncrafting Table
    diorama_quads.extend(extract_quads_from_model(ut, offset=(0, 0, 0)))

    # Back-right (-X): Powah Energy Cable at (-16, 0, 0) + Nitro Energy Cell at (-32, 0, 0)
    with open(os.path.join(POWAH_TEX_DIR, "cable.json")) as f:
        pc_core = json.load(f)
    diorama_quads.extend(extract_quads_from_model(pc_core, tex_overrides={"cable": "powah:energy_cable_nitro"}, offset=(-16, 0, 0)))
    diorama_quads.extend(extract_quads_from_model(build_powah_cable_arm("west"), offset=(-16, 0, 0)))
    diorama_quads.extend(extract_quads_from_model(build_powah_cable_arm("east"), offset=(-16, 0, 0)))
    diorama_quads.extend(extract_quads_from_model(ec, tex_overrides={"0": "powah:energy_cell_nitro"}, offset=(-32, 0, 0)))
    diorama_quads.extend(extract_quads_from_model(ec_core, offset=(-32, 0, 0), extra_shade=1.10))

    # Back-left (+Z): Pipez Item Pipe at (0, 0, +16) + Input Barrel at (0, 0, +32) + Hopper on top (0, 16, +32)
    with open(os.path.join(PIPEZ_TEX_DIR, "pipe_core.json")) as f:
        pz_core = json.load(f)
    with open(os.path.join(PIPEZ_TEX_DIR, "pipe_part.json")) as f:
        pz_part = json.load(f)
    with open(os.path.join(PIPEZ_TEX_DIR, "pipe_extract.json")) as f:
        pz_ext = json.load(f)

    diorama_quads.extend(extract_quads_from_model(pz_core, tex_overrides={"0": "pipez:item_pipe"}, offset=(0, 0, 16)))
    diorama_quads.extend(extract_quads_from_model(pz_part, tex_overrides={"0": "pipez:item_pipe"}, offset=(0, 0, 16), rot_y_90=0))  # toward table (-Z)
    diorama_quads.extend(extract_quads_from_model(pz_part, tex_overrides={"0": "pipez:item_pipe"}, offset=(0, 0, 16), rot_y_90=2))  # arm to barrel (+Z)
    diorama_quads.extend(extract_quads_from_model(pz_ext, tex_overrides={"0": "pipez:item_pipe"}, offset=(0, 0, 16), rot_y_90=2))   # extract flange from barrel (+Z)
    diorama_quads.extend(extract_quads_from_model(barrel, offset=(0, 0, 32)))

    # Left-side Output path: Pipez Item Pipe from Table (+16, 0, 0) turning back (+Z) to (+16, 0, +16) -> Output Barrel at (+32, 0, +16)
    diorama_quads.extend(extract_quads_from_model(pz_core, tex_overrides={"0": "pipez:item_pipe"}, offset=(16, 0, 0)))
    diorama_quads.extend(extract_quads_from_model(pz_part, tex_overrides={"0": "pipez:item_pipe"}, offset=(16, 0, 0), rot_y_90=3))  # arm to table (-X)
    diorama_quads.extend(extract_quads_from_model(pz_ext, tex_overrides={"0": "pipez:item_pipe"}, offset=(16, 0, 0), rot_y_90=3))   # extract flange from table (-X)
    diorama_quads.extend(extract_quads_from_model(pz_part, tex_overrides={"0": "pipez:item_pipe"}, offset=(16, 0, 0), rot_y_90=2))  # go +Z to (+16,0,+16)
    diorama_quads.extend(extract_quads_from_model(pz_core, tex_overrides={"0": "pipez:item_pipe"}, offset=(16, 0, 16)))
    diorama_quads.extend(extract_quads_from_model(pz_part, tex_overrides={"0": "pipez:item_pipe"}, offset=(16, 0, 16), rot_y_90=0)) # from -Z
    diorama_quads.extend(extract_quads_from_model(pz_part, tex_overrides={"0": "pipez:item_pipe"}, offset=(16, 0, 16), rot_y_90=1)) # into barrel (+X)
    diorama_quads.extend(extract_quads_from_model(barrel, offset=(32, 0, 16)))

    with open(os.path.join(MC_TEX_DIR, "hopper_model.json")) as f:
        hopper_model = json.load(f)
    diorama_quads.extend(extract_quads_from_model(hopper_model, offset=(0, 16, 32)))

    render_quads(diorama_quads, out_size=(1280, 960), yaw_deg=42.0, pitch_deg=27.0, ss=2, pad_frac=0.06).save(os.path.join(out_dir, "automation_diorama.png"))
    print("All 3D renders generated!")


def render_uncrafting_table_custom(out_path, model_dict, size=(900, 900), yaw_deg=55.0, pitch_deg=27.0):
    quads = extract_quads_from_model(model_dict)
    img = render_quads(quads, out_size=size, yaw_deg=yaw_deg, pitch_deg=pitch_deg, ss=2, pad_frac=0.07)
    img.save(out_path)


if __name__ == "__main__":
    render_all_3d_assets()
