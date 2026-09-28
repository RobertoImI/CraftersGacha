package org.crafterscr.craftersgacha.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Convierte los códigos típicos:
 *
 * &6 dorado
 * &e amarillo
 * &l negrita
 * &r reset
 *
 * en Componentes reales de Minecraft.
 */
public final class TextUtil {

    private TextUtil() {
    }

    public static MutableComponent colorize(String text) {
        MutableComponent result =
                Component.empty();

        Style currentStyle =
                Style.EMPTY;

        StringBuilder buffer =
                new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);

            if (current == '&' && i + 1 < text.length()) {
                char code =
                        Character.toLowerCase(
                                text.charAt(i + 1)
                        );

                ChatFormatting formatting =
                        ChatFormatting.getByCode(code);

                if (formatting != null) {
                    flush(
                            result,
                            buffer,
                            currentStyle
                    );

                    currentStyle =
                            currentStyle.applyLegacyFormat(
                                    formatting
                            );

                    i++;

                    continue;
                }
            }

            buffer.append(current);
        }

        flush(
                result,
                buffer,
                currentStyle
        );

        return result;
    }

    private static void flush(
            MutableComponent result,
            StringBuilder buffer,
            Style style
    ) {
        if (buffer.isEmpty()) {
            return;
        }

        result.append(
                Component.literal(
                        buffer.toString()
                ).setStyle(style)
        );

        buffer.setLength(0);
    }
}