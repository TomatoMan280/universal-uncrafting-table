from PIL import Image
import os

files = {
    'C:/Users/cecil/.gemini/antigravity-ide/brain/f5997b72-8d36-4782-971e-9877acf651e5/uncrafting_table_top_1781767172340.png': 'uncrafting_table_top.png',
    'C:/Users/cecil/.gemini/antigravity-ide/brain/f5997b72-8d36-4782-971e-9877acf651e5/uncrafting_table_front_1781767181563.png': 'uncrafting_table_front.png',
    'C:/Users/cecil/.gemini/antigravity-ide/brain/f5997b72-8d36-4782-971e-9877acf651e5/uncrafting_table_side_1781767189677.png': 'uncrafting_table_side.png'
}

out_dir = 'C:/Users/cecil/OneDrive/Documents/Minecraft Uncrafting Table Mod Creation/src/main/resources/assets/uncrafting_table/textures/block'

for src, dst in files.items():
    if os.path.exists(src):
        img = Image.open(src)
        img = img.resize((16, 16), Image.NEAREST)
        img.save(os.path.join(out_dir, dst))
        print(f'Saved {dst}')
