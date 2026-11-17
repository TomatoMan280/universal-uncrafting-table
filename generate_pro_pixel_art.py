import os
import random
from PIL import Image

out_dir = 'C:/Users/cecil/OneDrive/Documents/Minecraft Uncrafting Table Mod Creation/src/main/resources/assets/uncrafting_table/textures/block'

def h(hex_str):
    hex_str = hex_str.lstrip('#')
    return tuple(int(hex_str[i:i+2], 16) for i in (0, 2, 4))

# 1. Top Texture (Pro Dark Oak Grid + Lapis)
top = Image.new('RGB', (16, 16))
tp = top.load()

# Palette
wood_colors = [h('#2c1e16'), h('#38261b'), h('#422d20'), h('#4c3526')]
grid_dark = h('#140d0a')
grid_light = h('#5e4330')

# Base wood grain with noise
random.seed(42)
for y in range(16):
    for x in range(16):
        # Create horizontal grain with noise
        grain_val = (y + random.randint(0, 1)) % 4
        tp[x, y] = wood_colors[grain_val]

# Draw 3x3 grid (etched: dark shadow on top/left, highlight on bottom/right)
for y in range(16):
    for x in range(16):
        if x in (4, 10) or y in (4, 10):
            tp[x, y] = grid_dark
        if x in (5, 11) and y not in (4, 10) and y < 15:
            tp[x, y] = grid_light
        if y in (5, 11) and x not in (4, 10) and x < 15:
            tp[x, y] = grid_light

# Border
for i in range(16):
    tp[i, 0] = grid_dark
    tp[0, i] = grid_dark
    tp[i, 15] = grid_light
    tp[15, i] = grid_light
tp[15, 0] = grid_dark
tp[0, 15] = grid_dark

# Lapis indentation (Top-left 3x3 area)
lapis_base = h('#21499e')
lapis_light = h('#417aed')
lapis_highlight = h('#73a1ff')
lapis_dark = h('#11285e')
gold_trim = h('#fadc3e')
gold_shadow = h('#a37b12')

# Trim
tp[0,1] = tp[1,0] = tp[3,1] = tp[1,3] = gold_shadow
tp[0,2] = tp[2,0] = tp[3,2] = tp[2,3] = gold_trim

# Inner lapis
tp[1,1] = lapis_dark
tp[2,1] = lapis_base
tp[1,2] = lapis_highlight
tp[2,2] = lapis_light

top.save(os.path.join(out_dir, 'uncrafting_table_top.png'))

# 2. Side Texture (Pro Oak Drawers & Iron)
side = Image.new('RGB', (16, 16))
sp = side.load()

oak_colors = [h('#705332'), h('#8a6840'), h('#9c774b'), h('#a88254')]
iron_colors = [h('#383838'), h('#525252'), h('#707070'), h('#919191')]

for y in range(16):
    for x in range(16):
        # Oak base
        sp[x, y] = oak_colors[random.randint(1, 2)]

# Planks
for y in (0, 8):
    for x in range(16):
        if y+7 < 16:
            sp[x, y+7] = oak_colors[0] # shadow
        sp[x, y] = oak_colors[3] # highlight

# Drawers (inset)
for y in (3, 11):
    for x in range(3, 13):
        # Drawer shadow (top and left)
        sp[x, y] = oak_colors[0]
        sp[3, y+1] = oak_colors[0]
        sp[3, y+2] = oak_colors[0]
        # Drawer highlight (bottom and right)
        sp[x, y+3] = oak_colors[3]
        sp[12, y+1] = oak_colors[3]
        sp[12, y+2] = oak_colors[3]
        # Handles
        if x in (7, 8) and (y == 3 or y == 11):
            pass # just spacing
        if x in (7, 8) and (y == 4 or y == 12):
            sp[x, y] = iron_colors[3]
        if x in (7, 8) and (y == 5 or y == 13):
            sp[x, y] = iron_colors[1]

# Iron Trim
for y in range(16):
    sp[0, y] = iron_colors[1]
    sp[1, y] = iron_colors[2]
    sp[14, y] = iron_colors[2]
    sp[15, y] = iron_colors[0]
    # Edge glint
    if y % 4 == 1:
        sp[1, y] = iron_colors[3]
        sp[14, y] = iron_colors[3]

# Rivets
sp[1, 1] = sp[14, 1] = sp[1, 14] = sp[14, 14] = h('#1a1a1a')
sp[1, 2] = sp[14, 2] = sp[1, 13] = sp[14, 13] = h('#b3b3b3')

side.save(os.path.join(out_dir, 'uncrafting_table_side.png'))

# 3. Front Texture (Pro Redstone Groove)
front = Image.new('RGB', (16, 16))
fp = front.load()

rs_core = h('#ffffff')
rs_bright = h('#ff3333')
rs_mid = h('#cc0000')
rs_dark = h('#660000')

for y in range(16):
    for x in range(16):
        # Base same as side (oak planks)
        fp[x, y] = oak_colors[random.randint(1, 2)]

for y in (0, 8):
    for x in range(16):
        if y+7 < 16:
            fp[x, y+7] = oak_colors[0]
        fp[x, y] = oak_colors[3]

# Iron Trim (same as side)
for y in range(16):
    fp[0, y] = iron_colors[1]
    fp[1, y] = iron_colors[2]
    fp[14, y] = iron_colors[2]
    fp[15, y] = iron_colors[0]
    if y % 4 == 1:
        fp[1, y] = iron_colors[3]
        fp[14, y] = iron_colors[3]
sp[1, 1] = sp[14, 1] = sp[1, 14] = sp[14, 14] = h('#1a1a1a')
fp[1, 1] = fp[14, 1] = fp[1, 14] = fp[14, 14] = h('#1a1a1a')
fp[1, 2] = fp[14, 2] = fp[1, 13] = fp[14, 13] = h('#b3b3b3')

# Redstone Groove
for y in range(2, 15):
    # Groove shadow
    fp[6, y] = h('#2a1c12')
    fp[9, y] = h('#4a3321')
    
    # Redstone
    if y % 3 == 0:
        fp[7, y] = rs_mid
        fp[8, y] = rs_core
    elif y % 3 == 1:
        fp[7, y] = rs_bright
        fp[8, y] = rs_mid
    else:
        fp[7, y] = rs_dark
        fp[8, y] = rs_bright

front.save(os.path.join(out_dir, 'uncrafting_table_front.png'))
print("Generated PRO pixel art textures!")
