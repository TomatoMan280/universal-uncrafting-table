import json

model = {
    "parent": "minecraft:block/block",
    "textures": {
        "particle": "minecraft:block/dark_oak_planks",
        "top": "minecraft:block/dark_oak_planks",
        "legs": "minecraft:block/stripped_oak_log",
        "shelf": "minecraft:block/oak_planks",
        "clamp_blue": "minecraft:block/lapis_block",
        "clamp_metal": "minecraft:block/iron_block"
    },
    "elements": [
        {
            "name": "table_top",
            "from": [0, 9, 0],
            "to": [16, 12, 16],
            "faces": {
                "north": {"uv": [0, 13, 16, 16], "texture": "#top"},
                "east": {"uv": [0, 13, 16, 16], "texture": "#top"},
                "south": {"uv": [0, 13, 16, 16], "texture": "#top"},
                "west": {"uv": [0, 13, 16, 16], "texture": "#top"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                "down": {"uv": [0, 0, 16, 16], "texture": "#top"}
            }
        },
        {
            "name": "leg_fl",
            "from": [1, 0, 1],
            "to": [4, 9, 4],
            "faces": {
                "north": {"uv": [1, 7, 4, 16], "texture": "#legs"},
                "east": {"uv": [1, 7, 4, 16], "texture": "#legs"},
                "south": {"uv": [1, 7, 4, 16], "texture": "#legs"},
                "west": {"uv": [1, 7, 4, 16], "texture": "#legs"},
                "down": {"uv": [1, 1, 4, 4], "texture": "#legs"}
            }
        },
        {
            "name": "leg_fr",
            "from": [12, 0, 1],
            "to": [15, 9, 4],
            "faces": {
                "north": {"uv": [12, 7, 15, 16], "texture": "#legs"},
                "east": {"uv": [12, 7, 15, 16], "texture": "#legs"},
                "south": {"uv": [12, 7, 15, 16], "texture": "#legs"},
                "west": {"uv": [12, 7, 15, 16], "texture": "#legs"},
                "down": {"uv": [12, 1, 15, 4], "texture": "#legs"}
            }
        },
        {
            "name": "leg_bl",
            "from": [1, 0, 12],
            "to": [4, 9, 15],
            "faces": {
                "north": {"uv": [1, 7, 4, 16], "texture": "#legs"},
                "east": {"uv": [1, 7, 4, 16], "texture": "#legs"},
                "south": {"uv": [1, 7, 4, 16], "texture": "#legs"},
                "west": {"uv": [1, 7, 4, 16], "texture": "#legs"},
                "down": {"uv": [1, 12, 4, 15], "texture": "#legs"}
            }
        },
        {
            "name": "leg_br",
            "from": [12, 0, 12],
            "to": [15, 9, 15],
            "faces": {
                "north": {"uv": [12, 7, 15, 16], "texture": "#legs"},
                "east": {"uv": [12, 7, 15, 16], "texture": "#legs"},
                "south": {"uv": [12, 7, 15, 16], "texture": "#legs"},
                "west": {"uv": [12, 7, 15, 16], "texture": "#legs"},
                "down": {"uv": [12, 12, 15, 15], "texture": "#legs"}
            }
        },
        {
            "name": "bottom_shelf",
            "from": [2, 2, 2],
            "to": [14, 4, 14],
            "faces": {
                "north": {"uv": [2, 12, 14, 14], "texture": "#shelf"},
                "east": {"uv": [2, 12, 14, 14], "texture": "#shelf"},
                "south": {"uv": [2, 12, 14, 14], "texture": "#shelf"},
                "west": {"uv": [2, 12, 14, 14], "texture": "#shelf"},
                "up": {"uv": [2, 2, 14, 14], "texture": "#shelf"},
                "down": {"uv": [2, 2, 14, 14], "texture": "#shelf"}
            }
        },
        {
            "name": "vice_base",
            "from": [12, 12, 1],
            "to": [15, 14, 5],
            "faces": {
                "north": {"uv": [0, 0, 3, 2], "texture": "#clamp_blue"},
                "east": {"uv": [0, 0, 4, 2], "texture": "#clamp_blue"},
                "south": {"uv": [0, 0, 3, 2], "texture": "#clamp_blue"},
                "west": {"uv": [0, 0, 4, 2], "texture": "#clamp_blue"},
                "up": {"uv": [0, 0, 3, 4], "texture": "#clamp_blue"}
            }
        },
        {
            "name": "vice_jaw",
            "from": [11, 13, 2],
            "to": [12, 15, 4],
            "faces": {
                "north": {"uv": [1, 1, 2, 3], "texture": "#clamp_blue"},
                "east": {"uv": [1, 1, 3, 3], "texture": "#clamp_blue"},
                "south": {"uv": [1, 1, 2, 3], "texture": "#clamp_blue"},
                "west": {"uv": [1, 1, 3, 3], "texture": "#clamp_blue"},
                "up": {"uv": [1, 1, 2, 3], "texture": "#clamp_blue"}
            }
        },
        {
            "name": "vice_handle",
            "from": [15, 12.5, 2.5],
            "to": [17, 13.5, 3.5],
            "faces": {
                "north": {"uv": [0, 0, 2, 1], "texture": "#clamp_metal"},
                "east": {"uv": [0, 0, 1, 1], "texture": "#clamp_metal"},
                "south": {"uv": [0, 0, 2, 1], "texture": "#clamp_metal"},
                "west": {"uv": [0, 0, 1, 1], "texture": "#clamp_metal"},
                "up": {"uv": [0, 0, 2, 1], "texture": "#clamp_metal"},
                "down": {"uv": [0, 0, 2, 1], "texture": "#clamp_metal"}
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
print("Vanilla textures applied!")
