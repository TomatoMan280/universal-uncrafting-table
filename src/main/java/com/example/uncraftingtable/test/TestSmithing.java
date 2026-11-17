package com.example.uncraftingtable.test;

import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;

public class TestSmithing {
    public static void printMethods() {
        try (PrintWriter out = new PrintWriter(new FileWriter("smithing_methods.txt"))) {
            out.println("METHODS:");
            for (Method m : SmithingTransformRecipe.class.getDeclaredMethods()) {
                out.println(m.getName() + " " + m.getReturnType().getName());
            }
            out.println("FIELDS:");
            for (Field f : SmithingTransformRecipe.class.getDeclaredFields()) {
                out.println(f.getName() + " " + f.getType().getName());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
