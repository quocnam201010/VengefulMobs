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
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.EnumSet;
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
