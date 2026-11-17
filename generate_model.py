import json
import os

model = {
    "parent": "minecraft:block/block",
    "textures": {
        "particle": "uncrafting_table:block/uncrafting_table_top",
        "top": "uncrafting_table:block/uncrafting_table_top",
        "side": "uncrafting_table:block/uncrafting_table_side",
        "front": "uncrafting_table:block/uncrafting_table_front"
    },
    "elements": [
        {
            "name": "table_top",
            "from": [0, 9, 0],
            "to": [16, 12, 16],
            "faces": {
                "north": {"uv": [0, 13, 16, 16], "texture": "#side"},
                "east": {"uv": [0, 13, 16, 16], "texture": "#side"},
                "south": {"uv": [0, 13, 16, 16], "texture": "#side"},
                "west": {"uv": [0, 13, 16, 16], "texture": "#side"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                "down": {"uv": [0, 0, 16, 16], "texture": "#side"}
            }
        },
        {
            "name": "leg_fl",
            "from": [1, 0, 1],
            "to": [4, 9, 4],
            "faces": {
                "north": {"uv": [1, 7, 4, 16], "texture": "#side"},
                "east": {"uv": [1, 7, 4, 16], "texture": "#side"},
                "south": {"uv": [1, 7, 4, 16], "texture": "#side"},
                "west": {"uv": [1, 7, 4, 16], "texture": "#side"},
                "down": {"uv": [1, 1, 4, 4], "texture": "#side"}
            }
        },
        {
            "name": "leg_fr",
            "from": [12, 0, 1],
            "to": [15, 9, 4],
            "faces": {
                "north": {"uv": [12, 7, 15, 16], "texture": "#side"},
                "east": {"uv": [12, 7, 15, 16], "texture": "#side"},
                "south": {"uv": [12, 7, 15, 16], "texture": "#side"},
                "west": {"uv": [12, 7, 15, 16], "texture": "#side"},
                "down": {"uv": [12, 1, 15, 4], "texture": "#side"}
            }
        },
        {
            "name": "leg_bl",
            "from": [1, 0, 12],
            "to": [4, 9, 15],
            "faces": {
                "north": {"uv": [1, 7, 4, 16], "texture": "#side"},
                "east": {"uv": [1, 7, 4, 16], "texture": "#side"},
                "south": {"uv": [1, 7, 4, 16], "texture": "#side"},
                "west": {"uv": [1, 7, 4, 16], "texture": "#side"},
                "down": {"uv": [1, 12, 4, 15], "texture": "#side"}
            }
        },
        {
            "name": "leg_br",
            "from": [12, 0, 12],
            "to": [15, 9, 15],
            "faces": {
                "north": {"uv": [12, 7, 15, 16], "texture": "#side"},
                "east": {"uv": [12, 7, 15, 16], "texture": "#side"},
                "south": {"uv": [12, 7, 15, 16], "texture": "#side"},
                "west": {"uv": [12, 7, 15, 16], "texture": "#side"},
                "down": {"uv": [12, 12, 15, 15], "texture": "#side"}
            }
        },
        {
            "name": "bottom_shelf",
            "from": [2, 2, 2],
            "to": [14, 4, 14],
            "faces": {
                "north": {"uv": [1, 12, 15, 14], "texture": "#side"},
                "east": {"uv": [1, 12, 15, 14], "texture": "#side"},
                "south": {"uv": [1, 12, 15, 14], "texture": "#side"},
                "west": {"uv": [1, 12, 15, 14], "texture": "#side"},
                "up": {"uv": [1, 1, 15, 15], "texture": "#top"},
                "down": {"uv": [1, 1, 15, 15], "texture": "#side"}
            }
        },
        {
            "name": "vice_base",
            "from": [12, 12, 1],
            "to": [15, 14, 5],
            "faces": {
                "north": {"uv": [0, 0, 3, 2], "texture": "#side"},
                "east": {"uv": [0, 0, 4, 2], "texture": "#side"},
                "south": {"uv": [0, 0, 3, 2], "texture": "#side"},
                "west": {"uv": [0, 0, 4, 2], "texture": "#side"},
                "up": {"uv": [0, 0, 3, 4], "texture": "#side"}
            }
        },
        {
            "name": "vice_jaw",
            "from": [11, 13, 2],
            "to": [12, 15, 4],
            "faces": {
                "north": {"uv": [1, 1, 2, 3], "texture": "#side"},
                "east": {"uv": [1, 1, 3, 3], "texture": "#side"},
                "south": {"uv": [1, 1, 2, 3], "texture": "#side"},
                "west": {"uv": [1, 1, 3, 3], "texture": "#side"},
                "up": {"uv": [1, 1, 2, 3], "texture": "#side"}
            }
        },
        {
            "name": "vice_handle",
            "from": [15, 12.5, 2.5],
            "to": [17, 13.5, 3.5],
            "faces": {
                "north": {"uv": [0, 0, 2, 1], "texture": "#front"},
                "east": {"uv": [0, 0, 1, 1], "texture": "#front"},
                "south": {"uv": [0, 0, 2, 1], "texture": "#front"},
                "west": {"uv": [0, 0, 1, 1], "texture": "#front"},
                "up": {"uv": [0, 0, 2, 1], "texture": "#front"},
                "down": {"uv": [0, 0, 2, 1], "texture": "#front"}
            }
        }
    ],
    "display": {
        "gui": {
            "rotation": [30, 225, 0],
            "translation": [0, 0, 0],
            "scale": [0.625, 0.625, 0.625]
        },
        "thirdperson_righthand": {
            "rotation": [75, 45, 0],
            "translation": [0, 2.5, 0],
            "scale": [0.375, 0.375, 0.375]
        },
        "firstperson_righthand": {
            "rotation": [0, 45, 0],
            "translation": [0, 0, 0],
            "scale": [0.4, 0.4, 0.4]
        }
    }
}

path = "C:/Users/cecil/OneDrive/Documents/Minecraft Uncrafting Table Mod Creation/src/main/resources/assets/uncrafting_table/models/block/uncrafting_table.json"
with open(path, "w") as f:
    json.dump(model, f, indent=2)
print("Model created!")
