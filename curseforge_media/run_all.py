import subprocess
import sys
import os

HERE = os.path.dirname(os.path.abspath(__file__))

for script in ("mc_renderer.py", "sprite_helper.py", "generate_images.py"):
    path = os.path.join(HERE, script)
    print(f"=== Running {script} ===")
    subprocess.run([sys.executable, path], check=True)

print("All CurseForge showcase images regenerated cleanly in curseforge_media/output/")
