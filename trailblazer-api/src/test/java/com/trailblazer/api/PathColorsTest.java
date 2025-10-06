package com.trailblazer.api;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class PathColorsTest {

    @Test
    void paletteReturnsFixedList() {
        var palette = PathColors.palette();
        assertNotNull(palette);
        assertEquals(9, palette.size());
    }

    @Test
    void paletteIsUnmodifiable() {
        var palette = PathColors.palette();
        assertThatThrownBy(() -> palette.add(0xFF000000))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void assignColorForSameUuidReturnsSameColor() {
        UUID testUuid = UUID.randomUUID();
        int color1 = PathColors.assignColorFor(testUuid);
        int color2 = PathColors.assignColorFor(testUuid);
        assertEquals(color1, color2);
    }

    @Test
    void assignColorForNullReturnsFirstPaletteColor() {
        int color = PathColors.assignColorFor(null);
        assertEquals(PathColors.palette().get(0), color);
    }

    @Test
    void assignColorForReturnsColorFromPalette() {
        UUID testUuid = UUID.randomUUID();
        int color = PathColors.assignColorFor(testUuid);
        assertTrue(PathColors.palette().contains(color));
    }

    @Test
    void assignColorForDifferentUuidsCanReturnDifferentColors() {
        // Generate enough UUIDs to likely get different colors
        boolean foundDifferent = false;
        int firstColor = PathColors.assignColorFor(UUID.randomUUID());
        
        for (int i = 0; i < 50; i++) {
            int color = PathColors.assignColorFor(UUID.randomUUID());
            if (color != firstColor) {
                foundDifferent = true;
                break;
            }
        }
        
        assertTrue(foundDifferent, "Should eventually find UUIDs with different colors");
    }

    @Test
    void getColorNamesReturnsExpectedNames() {
        var names = PathColors.getColorNames();
        assertThat(names)
            .contains("red", "orange", "yellow", "green", "cyan", "blue", "purple", "pink", "white");
        assertEquals(9, names.size());
    }

    @Test
    void parseNullOrEmptyReturnsEmpty() {
        assertEquals(Optional.empty(), PathColors.parse(null));
        assertEquals(Optional.empty(), PathColors.parse(""));
    }

    @Test
    void parseValidColorNameReturnsColor() {
        Optional<Integer> red = PathColors.parse("red");
        assertTrue(red.isPresent());
        assertEquals(0xFFFF5555, red.get());

        Optional<Integer> blue = PathColors.parse("blue");
        assertTrue(blue.isPresent());
        assertEquals(0xFF5555FF, blue.get());
    }

    @Test
    void parseColorNameIsCaseInsensitive() {
        Optional<Integer> red1 = PathColors.parse("red");
        Optional<Integer> red2 = PathColors.parse("RED");
        Optional<Integer> red3 = PathColors.parse("Red");

        assertEquals(red1, red2);
        assertEquals(red1, red3);
    }

    @Test
    void parseHexWithHashReturnsColor() {
        Optional<Integer> color = PathColors.parse("#FF5555");
        assertTrue(color.isPresent());
        assertEquals(0xFFFF5555, color.get());
    }

    @Test
    void parseHexWithoutAlphaAddsFullAlpha() {
        Optional<Integer> color = PathColors.parse("#123456");
        assertTrue(color.isPresent());
        assertEquals(0xFF123456, color.get());
    }

    @Test
    void parseHexWithAlphaPreservesAlpha() {
        Optional<Integer> color = PathColors.parse("#80ABCDEF");
        assertTrue(color.isPresent());
        assertEquals(0x80ABCDEF, color.get());
    }

    @Test
    void parseInvalidHexReturnsEmpty() {
        assertEquals(Optional.empty(), PathColors.parse("#GGGGGG"));
        assertEquals(Optional.empty(), PathColors.parse("#12345")); // Too short
        assertEquals(Optional.empty(), PathColors.parse("#1234567")); // Invalid length
    }

    @Test
    void parseInvalidNameReturnsEmpty() {
        assertEquals(Optional.empty(), PathColors.parse("notacolor"));
    }

    @Test
    void nameOrHexReturnsNameForPaletteColors() {
        assertEquals("red", PathColors.nameOrHex(0xFFFF5555));
        assertEquals("blue", PathColors.nameOrHex(0xFF5555FF));
        assertEquals("white", PathColors.nameOrHex(0xFFFFFFFF));
    }

    @Test
    void nameOrHexReturnsHexForCustomColors() {
        String hex = PathColors.nameOrHex(0xFF123456);
        assertEquals("#123456", hex);
    }

    @Test
    void nameOrHexIgnoresAlpha() {
        // Different alpha values should produce the same hex string
        String hex1 = PathColors.nameOrHex(0xFF123456);
        String hex2 = PathColors.nameOrHex(0x80123456);
        assertEquals("#123456", hex1);
        assertEquals("#123456", hex2);
    }

    @Test
    void roundTripParseAndNameOrHex() {
        String originalHex = "#ABCDEF";
        Optional<Integer> parsed = PathColors.parse(originalHex);
        assertTrue(parsed.isPresent());
        
        String hex = PathColors.nameOrHex(parsed.get());
        assertEquals("#ABCDEF", hex);
    }

    @Test
    void roundTripParseAndNameOrHexForNames() {
        String colorName = "green";
        Optional<Integer> parsed = PathColors.parse(colorName);
        assertTrue(parsed.isPresent());
        
        String name = PathColors.nameOrHex(parsed.get());
        assertEquals(colorName, name);
    }
}
