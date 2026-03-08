# <img src="icon.png"> Glamourer

The RuneLite plugin for changing appearances of Old School RuneScape items. Customize the colors of nearly any item for
fashion, accessibility, or just because you feel like it.

Unlike similar recoloring plugins that just overlay an image on top of the item's default icon, Glamourer modifies the
item composition at a deeper level which changes items in your inventory, equipment, and everywhere else it is visible.

Join [Discord](https://discord.gg/B6dD9R5U36) to share your creations, get technical support, and chat with others.

## Features

- **Item Recoloring**: Change the colors of any item in the game and see it in your inventory, equipment, on the ground, etc.
- **Texture Replacing**: Change the texture of any textured item (e.g. fire cape, infernal cape) with any other game texture
- **Glamour Plates**: Organize multiple item recolors into glamour plates that can be enabled/disabled together
- **Display Style**: Choose whether a glamours appear only on yourself or also on other players
- **Color Groups**: Similar colors on an item are automatically grouped, allowing batch editing with a single picker
- **Import/Export**: Share plates with your friends by importing and exporting JSON

## Usage

Install Glamourer from the RuneLite plugin hub. To use Glamourer, open the panel from the RuneLite sidebar (<img src="icon.png">).

### Make your own glamours
1. Press the "**+**" button at the top right to create a new, empty plate
1. Press "**+ Search for Item**" to search for and add items to your plate
1. Click on a color swatch to open the color picker
1. When you press "OK", the new color will immediately apply to the item's icon and your equipment

> [!NOTE]
> Some colors are only used by the inventory icon or by the equipment and not both.
> There is no indicator if the color is only used by one or the other.
> Your edit may change the icon and not your equipped item or vice versa.

### HSL Picker
Runescape uses "Jagex" color instead of more standard formats like RGB. Jagex color is a variant of HSL (Hue, Saturation, Luminance). The "HSL" value in the color picker represents a unique color. The "Hue", "Sat", "Lum" sliders change individual components of the full HSL.

When you open the color picker, the HSL textbox is automatically selected. If you want to copy/paste colors, you can press **ctrl+c** or ![copy.png](src/main/resources/io/huze/glamourer/ui/copy.png) to copy or **ctrl+v** to paste.

<img src="readme_assets/color_picker.png" alt="Color Picker Example">

On the left side of the picker are the following:
1. Current color preview
2. Previous color - what you opened the picker with
3. Original color - what color the item had before edits

On the right side of the picker are the following:
1. **Hue** - the chromatic component. Think of this as a "color wheel."
2. **Sat** - the color intensity. Low values have less color (more black & white), high values have more color.
3. **Lum** - The color brightness. Low values are darker, high values are lighter.

> [!NOTE]
> The current color preview will not accurately represent what the color looks like in-game at high Sat and high Lum.
> The preview may appear white but actually be a very bright color; lower the Saturation if you actually want white.

### Import from JSON
1. Click the Import button (<img src="./src/main/resources/io/huze/glamourer/ui/import.png">) to open the import **dialog**
1. Copy JSON from somewhere, such as the Glamourer Discord server
1. Right click paste or ctrl+v into the textbox
1. Press import
   * If you have already imported the plate before, you can choose to overwrite the existing plate instead 

### Change the way other people look
By default, glamours only affect your character and not other people. If you want to change the way an item looks for
everyone, right-click the plate header and press "![global.png](src/main/resources/io/huze/glamourer/ui/global.png) Show on everyone."

If your glamours are affecting everyone and you want to undo it, right-click the plate header and press "![local.png](src/main/resources/io/huze/glamourer/ui/local.png) Show on self only." 

## Examples

### Recolor
<img src="./readme_assets/dragon_zombie_axe.webp">

### Import / Export
<img src="./readme_assets/import_export.webp">

## Contributing

I am open for small contributions. I will likely reject large contributions; reach out to discuss what you want before changing anything.

## Known Issues
* Recoloring herb seeds does not work properly.
  * I have opened an issue on RuneLite to fix: https://github.com/runelite/runelite/issues/19913
  * No workaround