package com.example.uncraftingtable;
import net.minecraft.world.item.alchemy.PotionBrewing;
import java.lang.reflect.Field;
public class Test2 {
    public void test() {
        for (Field f : PotionBrewing.class.getDeclaredFields()) {
            System.out.println(f.getName() + " : " + f.getType().getName());
        }
    }
}
