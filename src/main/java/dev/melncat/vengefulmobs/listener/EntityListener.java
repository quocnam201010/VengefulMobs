package dev.melncat.vengefulmobs.listener;

import com.destroystokyo.paper.entity.ai.*;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import dev.melncat.vengefulmobs.VengefulMobs;
import dev.melncat.vengefulmobs.config.Config;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public class EntityListener implements Listener {
	private final VengefulMobs plugin;
	
	public EntityListener(VengefulMobs plugin) {
		this.plugin = plugin;
	}
	
	@EventHandler
	private void on(EntityAddToWorldEvent event) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		Entity entity = event.getEntity();
		if (!plugin.config().isEnabled(entity.getType())) return;
		if (!(entity instanceof Creature mob)) return; // This should never happen
		Config.MobConfig config = plugin.config().fromType(entity.getType());
		if (mob.getAttribute(Attribute.ATTACK_DAMAGE) == null) {
			mob.registerAttribute(Attribute.ATTACK_DAMAGE);
		}
		Objects.requireNonNull(mob.getAttribute(Attribute.ATTACK_DAMAGE))
			.setBaseValue(config.damage());

		// Apply custom attribute modifiers configured for this entity type
		List<Config.AttributeModifierConfig> customModifiers = plugin.config().getAttributeModifiers(entity.getType());
		for (Config.AttributeModifierConfig mod : customModifiers) {
			AttributeInstance attrInst = mob.getAttribute(mod.attribute());
			if (attrInst == null) {
				try {
					mob.registerAttribute(mod.attribute());
					attrInst = mob.getAttribute(mod.attribute());
				} catch (IllegalArgumentException e) {
					plugin.getLogger().warning("Could not register attribute " + mod.attribute().getKey().toString() + " for " + entity.getType().name() + ": " + e.getMessage());
				}
			}
			if (attrInst != null) {
				boolean exists = false;
				for (AttributeModifier existingMod : attrInst.getModifiers()) {
					if (existingMod.getKey().equals(mod.id())) {
						exists = true;
						break;
					}
				}
				if (!exists) {
					AttributeModifier newMod = new AttributeModifier(
						mod.id(),
						mod.value(),
						mod.operator(),
						EquipmentSlotGroup.ANY
					);
					if (mod.attribute() == Attribute.MAX_HEALTH) {
						double oldMax = attrInst.getValue();
						double currentHealth = mob.getHealth();
						attrInst.addModifier(newMod);
						double newMax = attrInst.getValue();
						if (oldMax > 0) {
							double newHealth = currentHealth * (newMax / oldMax);
							mob.setHealth(Math.max(0.0, Math.min(newHealth, newMax)));
						} else {
							mob.setHealth(newMax);
						}
					} else {
						attrInst.addModifier(newMod);
					}
				}
			}
		}

		MobGoals goals = Bukkit.getMobGoals();
		net.minecraft.world.entity.Entity nmsEntity = (net.minecraft.world.entity.Entity)
			mob.getClass().getMethod("getHandle").invoke(mob);
		if (!(nmsEntity instanceof PathfinderMob handle)) return;
		goals.addGoal(mob, 1, new PaperVanillaGoal<>(new MeleeAttackGoal(handle, config.speed(), false)));
		goals.removeGoal(mob, VanillaGoal.PANIC);
		switch (config.mode()) {
			case RETALIATE, RETALIATE_ONCE -> goals.addGoal(mob, 1, new PaperVanillaGoal<>(
				new HurtByTargetGoal(handle)
			));
			case RETALIATE_WITH_SUPPORT -> {
				goals.addGoal(mob, 1, new PaperVanillaGoal<>(
					new HurtByTargetGoal(handle)
						.setAlertOthers()
				));
				goals.addGoal(mob, 2, new Goal<>() {
					private final EnumSet<GoalType> type = EnumSet.of(GoalType.TARGET);
					private final GoalKey<Creature> key = GoalKey.of(Creature.class, new NamespacedKey(plugin, "clear_dead_target"));
					
					@Override
					public boolean shouldActivate() {
						Entity target = mob.getTarget();
						return target != null && target.isDead();
					}
					
					@Override
					public void start() {
						mob.setTarget(null);
						stop();
					}
					
					@Override
					public @NotNull GoalKey<Creature> getKey() {
						return key;
					}
					
					@Override
					public @NotNull EnumSet<GoalType> getTypes() {
						return type;
					}
				});
			}
			case HOSTILE -> goals.addGoal(mob, 3, new PaperVanillaGoal<>(
				new NearestAttackableTargetGoal<>(handle, Player.class, true)
			));
			case MURDER_ALL -> goals.addGoal(mob, 3, new PaperVanillaGoal<>(
				new NearestAttackableTargetGoal<>(handle, LivingEntity.class, 10, true, true, (target, level) -> target.attackable())
			));
			case MURDER_OTHERS -> goals.addGoal(mob, 3, new PaperVanillaGoal<>(
				new NearestAttackableTargetGoal<>(handle, LivingEntity.class, 10, true, true,
					(target, level) -> target.attackable() && target.getType() != handle.getType())
			));
		}
	}
	
	@EventHandler
	private void on(EntityDamageByEntityEvent event) {
		Entity damager = event.getDamager();
		if (!plugin.config().isEnabled(damager.getType())) return;
		if (!(damager instanceof Creature c)) return;
		Config.MobConfig config = plugin.config().fromType(damager.getType());
		if (config.mode() == Config.MobConfig.Mode.RETALIATE_ONCE) {
			c.setTarget(null);
			c.setKiller(null);
			Goal<?> goal = Bukkit.getMobGoals().getGoal(c, VanillaGoal.HURT_BY);
			if (goal != null) goal.stop();
		}
	}
}
