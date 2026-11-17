import os
from PIL import Image

out_dir = 'C:/Users/cecil/OneDrive/Documents/Minecraft Uncrafting Table Mod Creation/src/main/resources/assets/uncrafting_table/textures/block'

# Hex to RGB helper
def h(hex_str):
    hex_str = hex_str.lstrip('#')
    return tuple(int(hex_str[i:i+2], 16) for i in (0, 2, 4))

# 1. Top Texture (Dark Oak Grid + Lapis)
top = Image.new('RGB', (16, 16))
top_pixels = top.load()

dark_oak_base = h('#3c2712')
dark_oak_light = h('#4a331a')
grid_line = h('#1c1c1c')
lapis_dark = h('#1c4d9c')
lapis_light = h('#347cf0')

for y in range(16):
    for x in range(16):
        # Checkerboard-ish noise for wood texture
        if (x + y) % 2 == 0:
            top_pixels[x, y] = dark_oak_base
        else:
            top_pixels[x, y] = dark_oak_light
        
        # 3x3 Grid lines (at x=4, x=10, y=4, y=10)
        if x in (4, 10) or y in (4, 10):
            top_pixels[x, y] = grid_line
            
        # Outer border
        if x == 0 or x == 15 or y == 0 or y == 15:
            top_pixels[x, y] = grid_line

# Lapis indentation in top-left
top_pixels[1, 1] = lapis_dark
top_pixels[2, 1] = lapis_light
top_pixels[1, 2] = lapis_light
top_pixels[2, 2] = lapis_dark

top.save(os.path.join(out_dir, 'uncrafting_table_top.png'))

# 2. Side Texture (Oak Planks with Drawers & Iron Trim)
side = Image.new('RGB', (16, 16))
side_pixels = side.load()

oak_base = h('#a2824e')
oak_dark = h('#8f703e')
iron_base = h('#8f8f8f')
iron_dark = h('#5a5a5a')
drawer_base = h('#6d5028')
drawer_shadow = h('#4a331a')

for y in range(16):
    for x in range(16):
        # Oak planks
        if x % 4 == 0 or y % 6 == 0:
            side_pixels[x, y] = oak_dark
        else:
            side_pixels[x, y] = oak_base
            
        # Drawers (Top: y=3..6, Bottom: y=9..12, x=3..12)
        if (3 <= y <= 6 or 9 <= y <= 12) and 3 <= x <= 12:
            if y == 6 or y == 12 or x == 12:
                side_pixels[x, y] = drawer_shadow
            else:
                side_pixels[x, y] = drawer_base
        
        # Drawer handles
        if (y == 4 or y == 10) and (x == 7 or x == 8):
            side_pixels[x, y] = iron_base
            
        # Iron Trim (Edges)
        if x == 0 or x == 15 or y == 0 or y == 15:
            if (x+y)%2 == 0:
                side_pixels[x, y] = iron_dark
            else:
                side_pixels[x, y] = iron_base

side.save(os.path.join(out_dir, 'uncrafting_table_side.png'))

# 3. Front Texture (Oak Planks with Redstone Groove & Iron Trim)
front = Image.new('RGB', (16, 16))
front_pixels = front.load()

redstone_bright = h('#ff3333')
redstone_dark = h('#990000')
groove_dark = h('#222222')

for y in range(16):
    for x in range(16):
        # Oak planks
        if x % 4 == 0 or y % 6 == 0:
            front_pixels[x, y] = oak_dark
        else:
            front_pixels[x, y] = oak_base
            
        # Redstone groove in center (x=7,8, y=3..12)
        if 3 <= y <= 12 and (x == 7 or x == 8):
            if x == 7:
                front_pixels[x, y] = groove_dark
            else:
                if y % 3 == 0:
                    front_pixels[x, y] = redstone_bright
                else:
                    front_pixels[x, y] = redstone_dark
            
        # Iron Trim (Edges)
        if x == 0 or x == 15 or y == 0 or y == 15:
            if (x+y)%2 == 0:
                front_pixels[x, y] = iron_dark
            else:
                front_pixels[x, y] = iron_base

front.save(os.path.join(out_dir, 'uncrafting_table_front.png'))

print("Generated crisp pixel art textures!")
