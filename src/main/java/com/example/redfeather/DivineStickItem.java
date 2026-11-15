package com.example.redfeather;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public class DivineStickItem extends Item {

    private static final double BONUS_DAMAGE = 99.0;
    private static final double BONUS_SPEED = 96.0;

    public DivineStickItem(Item.Properties properties) {
        super(properties
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant()
                .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                .attributes(createAttributes()));
    }

    private static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, BONUS_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, BONUS_SPEED, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }
}
