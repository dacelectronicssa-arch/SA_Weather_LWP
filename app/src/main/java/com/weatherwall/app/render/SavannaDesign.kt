package com.weatherwall.app.render

/** Savanna settings, from the in-chat savanna designers (landscape, herd and animal studio). */
object SavannaDesign {
    // Landscape
    const val HORIZON = 0.6f
    const val HILLS = 1f
    // Grass colour follows the season: 0 golden-brown .. 1 green. Greenest at the end of winter,
    // driest at the end of summer (see Season).
    const val GRASS_DRY = 0.05f
    const val GRASS_GREEN = 0.85f
    const val GREEN_PATCHES = 0.5f
    const val RED_EARTH = 0.35f
    const val GRASS_DETAIL = 1.55f
    const val BIG_TREE_SIZE = 0.85f
    const val HORIZON_TREES = 16
    const val LAYOUT_SEED = 21

    // Herd
    const val ZEBRA = 6
    const val WILDEBEEST = 10
    const val ELEPHANTS = 3
    const val GIRAFFES = 2
    const val SPRINGBOK = 8
    const val LIONS = 3
    const val ANIMAL_SIZE = 1f
    const val NEAREST_ANIMALS = 0.45f
    /** Elephants keep to this band of the plain (0 = horizon, NEAREST_ANIMALS = front of the herd). */
    const val ELEPHANT_BACK = 0.08f
    const val ELEPHANT_FRONT = 0.2f
    const val HERD_DEPTH = 1.6f
    const val ACTIVITY = 0.6f
    const val GRASS_SWAY = 0.4f
    const val VULTURE_SIZE = 1.9f
    const val VULTURES = 3

    // Egyptian geese flying over in a V
    const val FLOCK_BIRDS = 11
    const val FLOCK_SIZE = 1f

    // Lions (lion studio)
    const val LION_MANE_SIZE = 1f
    const val LION_MANE_DARK = 0.6f
    const val LION_COAT = 0.5f

    // The designers' canvas, for scaling their pixel sizes onto the phone.
    const val PREVIEW_WIDTH = 270f
}
