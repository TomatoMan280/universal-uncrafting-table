import json
import os
from PIL import Image

out_dir = 'C:/Users/cecil/OneDrive/Documents/Minecraft Uncrafting Table Mod Creation/src/main/resources/assets/uncrafting_table/textures/block'

def h(hex_str):
    hex_str = hex_str.lstrip('#')
    return tuple(int(hex_str[i:i+2], 16) for i in (0, 2, 4))

# 1. Green Self-Healing Mat
mat = Image.new('RGB', (16, 16))
mp = mat.load()

base_green = h('#2c633a')
grid_color = h('#74a881')
dark_edge = h('#1d4727')

for y in range(16):
    for x in range(16):
        # Base
        mp[x, y] = base_green
        # Grid lines every 3 pixels
        if x % 3 == 0 or y % 3 == 0:
            mp[x, y] = grid_color
        # Edges
        if x == 0 or x == 15 or y == 0 or y == 15:
            mp[x, y] = dark_edge

mat.save(os.path.join(out_dir, 'uncrafting_table_mat.png'))

# 2. Screwdriver
sd = Image.new('RGB', (16, 16), (255, 0, 255)) # magenta transparent bg
sd.putalpha(255)
sp = sd.load()

# clear bg to transparent
for y in range(16):
    for x in range(16):
        sp[x,y] = (0, 0, 0, 0)

handle_red = (200, 30, 30, 255)
handle_dark = (120, 20, 20, 255)
metal = (200, 200, 200, 255)
metal_dark = (100, 100, 100, 255)
yellow = (255, 200, 0, 255)

# Handle (x=2..6)
for x in range(2, 7):
    sp[x, 7] = handle_dark
    sp[x, 8] = handle_red
    sp[x, 9] = handle_dark

# Yellow stripes on handle
sp[3, 8] = yellow
sp[5, 8] = yellow

# Shaft (x=7..13)
for x in range(7, 14):
    sp[x, 8] = metal

# Tip (x=14)
sp[14, 8] = metal_dark

sd.save(os.path.join(out_dir, 'uncrafting_table_screwdriver.png'))

# 3. Update Model
model_path = "C:/Users/cecil/OneDrive/Documents/Minecraft Uncrafting Table Mod Creation/src/main/resources/assets/uncrafting_table/models/block/uncrafting_table.json"

with open(model_path, 'r') as f:
    model = json.load(f)

# Add textures
model['textures']['mat'] = "uncrafting_table:block/uncrafting_table_mat"
model['textures']['screwdriver'] = "uncrafting_table:block/uncrafting_table_screwdriver"

# Add Mat Element
mat_element = {
    "name": "cutting_mat",
    "from": [3, 12, 3],
    "to": [13, 12.1, 13],
    "faces": {
        "up": {"uv": [0, 0, 16, 16], "texture": "#mat"},
        "north": {"uv": [0, 0, 16, 1], "texture": "#mat"},
        "east": {"uv": [0, 0, 16, 1], "texture": "#mat"},
        "south": {"uv": [0, 0, 16, 1], "texture": "#mat"},
        "west": {"uv": [0, 0, 16, 1], "texture": "#mat"}
    }
}

# Add Screwdriver Element (Left side, laying flat)
sd_element = {
    "name": "screwdriver",
    "from": [1, 12, 6],
    "to": [2, 12.2, 14],
    "rotation": {"origin": [1.5, 12, 10], "axis": "y", "angle": 22.5},
    "faces": {
        "up": {"uv": [7, 2, 15, 3], "rotation": 90, "texture": "#screwdriver"},
        "north": {"uv": [7, 2, 8, 3], "texture": "#screwdriver"},
        "east": {"uv": [7, 2, 15, 3], "texture": "#screwdriver"},
        "south": {"uv": [14, 2, 15, 3], "texture": "#screwdriver"},
        "west": {"uv": [7, 2, 15, 3], "texture": "#screwdriver"}
    }
}

model['elements'].append(mat_element)
model['elements'].append(sd_element)

with open(model_path, 'w') as f:
    json.dump(model, f, indent=2)

print("Generated mat and screwdriver textures and updated model!")
