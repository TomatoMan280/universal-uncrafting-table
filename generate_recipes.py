import os
import json

base_dir = "C:/Users/cecil/OneDrive/Documents/Minecraft Uncrafting Table Mod Creation/src/main/resources/data/uncrafting_table/recipe"
os.makedirs(base_dir, exist_ok=True)

recipes = {
    "uncrafting_table.json": {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [
            "ILI",
            "DCD",
            "OOO"
        ],
        "key": {
            "I": { "item": "minecraft:iron_ingot" },
            "L": { "item": "minecraft:lapis_block" },
            "D": { "item": "minecraft:diamond" },
            "C": { "item": "minecraft:crafting_table" },
            "O": { "item": "minecraft:obsidian" }
        },
        "result": { "id": "uncrafting_table:uncrafting_table", "count": 1 }
    },
    "basic_speed_card.json": {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [
            "RIR",
            "IPI",
            "RIR"
        ],
        "key": {
            "R": { "item": "minecraft:redstone" },
            "I": { "item": "minecraft:iron_ingot" },
            "P": { "item": "minecraft:paper" }
        },
        "result": { "id": "uncrafting_table:basic_speed_card", "count": 1 }
    },
    "advanced_speed_card.json": {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [
            "RGR",
            "GBG",
            "RGR"
        ],
        "key": {
            "R": { "item": "minecraft:redstone" },
            "G": { "item": "minecraft:gold_ingot" },
            "B": { "item": "uncrafting_table:basic_speed_card" }
        },
        "result": { "id": "uncrafting_table:advanced_speed_card", "count": 1 }
    },
    "elite_speed_card.json": {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [
            "RDR",
            "DAD",
            "RDR"
        ],
        "key": {
            "R": { "item": "minecraft:redstone" },
            "D": { "item": "minecraft:diamond" },
            "A": { "item": "uncrafting_table:advanced_speed_card" }
        },
        "result": { "id": "uncrafting_table:elite_speed_card", "count": 1 }
    },
    "ultimate_speed_card.json": {
        "type": "minecraft:smithing_transform",
        "addition": { "item": "minecraft:netherite_ingot" },
        "base": { "item": "uncrafting_table:elite_speed_card" },
        "template": { "item": "minecraft:netherite_upgrade_smithing_template" },
        "result": { "id": "uncrafting_table:ultimate_speed_card" }
    }
}

for filename, recipe in recipes.items():
    with open(os.path.join(base_dir, filename), "w") as f:
        json.dump(recipe, f, indent=2)

print("Recipes created successfully.")
