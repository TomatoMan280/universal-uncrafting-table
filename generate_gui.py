from PIL import Image, ImageDraw

bg_color = (35, 39, 42, 255)
border_top = (50, 53, 58, 255)
border_bot = (20, 22, 24, 255)
slot_bg = (24, 25, 28, 255)
slot_shadow = (15, 16, 18, 255)
slot_highlight = (45, 48, 52, 255)

W, H = 256, 228
img = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

# Main container panel (256 x 228)
draw.rectangle([0, 0, W - 1, H - 1], fill=bg_color)
draw.rectangle([0, 0, W - 1, 1], fill=border_top)
draw.rectangle([0, 0, 1, H - 1], fill=border_top)
draw.rectangle([W - 2, 0, W - 1, H - 1], fill=border_bot)
draw.rectangle([0, H - 2, W - 1, H - 1], fill=border_bot)


def draw_slot(x, y):
    draw.rectangle([x - 1, y - 1, x + 16, y + 16], fill=slot_bg)
    draw.rectangle([x - 1, y - 1, x + 16, y - 1], fill=slot_shadow)
    draw.rectangle([x - 1, y - 1, x - 1, y + 16], fill=slot_shadow)
    draw.rectangle([x, y + 16, x + 16, y + 16], fill=slot_highlight)
    draw.rectangle([x + 16, y, x + 16, y + 16], fill=slot_highlight)


def draw_bar_bg(x, y, w, h):
    draw.rectangle([x - 1, y - 1, x + w, y + h], fill=slot_bg)
    draw.rectangle([x - 1, y - 1, x + w, y - 1], fill=slot_shadow)
    draw.rectangle([x - 1, y - 1, x - 1, y + h], fill=slot_shadow)
    draw.rectangle([x, y + h, x + w, y + h], fill=slot_highlight)
    draw.rectangle([x + w, y, x + w, y + h], fill=slot_highlight)


# Input 4x4 (x=8..80, y=20..92)
for row in range(4):
    for col in range(4):
        draw_slot(8 + col * 18, 20 + row * 18)

# Output 4x4 (x=164..236, y=20..92)
for row in range(4):
    for col in range(4):
        draw_slot(164 + col * 18, 20 + row * 18)

# Energy bar (x=240..246, y=20..92)
draw_bar_bg(240, 20, 6, 72)

# Books (x=16, y=105) & Speed (x=52, y=105)
draw_slot(16, 105)
draw_slot(52, 105)

# 4 Modifiers (x=164, 182, 200, 218; y=105)
for i in range(4):
    draw_slot(164 + i * 18, 105)

# Player inventory 3x9 (x=47, y=145)
for row in range(3):
    for col in range(9):
        draw_slot(47 + col * 18, 145 + row * 18)

# Hotbar 1x9 (x=47, y=203)
for col in range(9):
    draw_slot(47 + col * 18, 203)

img.save(r'c:\Users\cecil\OneDrive\Documents\Minecraft Uncrafting Table Mod Creation\src\main\resources\assets\uncrafting_table\textures\gui\container\uncrafting_table.png')
print("Generated 256x228 container GUI texture!")
