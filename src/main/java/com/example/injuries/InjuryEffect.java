package com.example.injuries;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * A purely cosmetic status effect: HUD icon + name + timer. It has no gameplay of its own; all penalties come from
 * the injury's datapack definition. Reference it from an injury with "hud_effect".
 */
public class InjuryEffect extends MobEffect {
    public InjuryEffect(int color) {
        super(MobEffectCategory.HARMFUL, color);
    }
}
