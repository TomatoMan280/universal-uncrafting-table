from PIL import Image
import colorsys

# Load the gold advanced card
img = Image.open('src/main/resources/assets/uncrafting_table/textures/item/advanced_speed_card.png').convert('RGBA')
pixels = img.load()

for y in range(img.height):
    for x in range(img.width):
        r, g, b, a = pixels[x, y]
        if a > 0:
            # Convert to HSV
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            
            # If the color is yellowish (hue between 30 and 75 degrees roughly)
            if 0.05 < h < 0.25 and s > 0.3:
                # Shift hue to cyan (diamond)
                h = 0.5  # 180 degrees (cyan)
                
            # Convert back to RGB
            nr, ng, nb = colorsys.hsv_to_rgb(h, s, v)
            pixels[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)

# Save as the new elite card
img.save('src/main/resources/assets/uncrafting_table/textures/item/elite_speed_card.png')
print("Successfully generated elite_speed_card.png")
