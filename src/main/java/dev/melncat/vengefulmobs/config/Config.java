package dev.melncat.vengefulmobs.config;

import dev.melncat.vengefulmobs.VengefulMobs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Creature;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class Config {
	private final VengefulMobs plugin;
	
	public Config(VengefulMobs plugin) {
		this.plugin = plugin;
	}
	
	private MobConfig defaultConfig;
	private EnumSet<EntityType> enabled;
	
	private final EnumMap<EntityType, MobConfig> overrides = new EnumMap<>(EntityType.class);
	
	public void loadConfig(ConfigurationSection section) {
		ConfigurationSection mobs = Objects.requireNonNull(section.getConfigurationSection("mobs"));
		defaultConfig = MobConfig.makeDefault(Objects.requireNonNull(mobs.getConfigurationSection("default")));
		try {
			enabled = mobs.getStringList("enabled")
				.stream()
				.map(x -> EntityType.valueOf(x.toUpperCase()))
				.filter(x -> {
					if (x.getEntityClass() != null && Creature.class.isAssignableFrom(x.getEntityClass())) return true;
					plugin.getLogger().warning(x.name() + " is not a valid living entity.");
					return false;
				})
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(EntityType.class)));
		} catch (IllegalArgumentException e) {
			plugin.getLogger().severe(e.getLocalizedMessage());
		}
		ConfigurationSection overrideConfig = mobs.getConfigurationSection("overrides");
		overrides.clear();
		if (overrideConfig != null)
			for (String key : overrideConfig.getKeys(false)) {
				try {
					EntityType type = EntityType.valueOf(key.toUpperCase());
					overrides.put(type, MobConfig.fromSection(Objects.requireNonNull(overrideConfig.getConfigurationSection(key)), defaultConfig));
				} catch (IllegalArgumentException e) {
					plugin.getLogger().severe(e.getLocalizedMessage());
				}
			}
	}
	
	public boolean isEnabled(EntityType type) {
		return enabled.contains(type);
	}
	
	public @NotNull MobConfig fromType(EntityType type) {
		return overrides.getOrDefault(type, defaultConfig);
	}
	
	
	public static class MobConfig {
		public enum Mode {
			RETALIATE,
			RETALIATE_ONCE,
			RETALIATE_WITH_SUPPORT,
			HOSTILE,
			MURDER_ALL,
			MURDER_OTHERS
		}
		
		private final double damage;
		private final double speed;
		private final Mode mode;
		
		private MobConfig(double damage, double speed, Mode mode) {
			this.damage = damage;
			this.speed = speed;
			this.mode = mode;
		}
		
		public double damage() {
			return damage;
		}
		
		public double speed() {
			return speed;
		}
		
		public Mode mode() {
			return mode;
		}
		
		protected static MobConfig makeDefault(ConfigurationSection section) {
			return new MobConfig(
				section.getDouble("damage"),
				section.getDouble("speed"),
				Mode.valueOf(Objects.requireNonNull(section.getString("mode")).toUpperCase())
			);
		}
		
		protected static MobConfig fromSection(ConfigurationSection section, MobConfig defaultConfig) throws IllegalArgumentException {
			return new MobConfig(
				section.getDouble("damage", defaultConfig.damage()),
				section.getDouble("speed", defaultConfig.speed()),
				Optional.ofNullable(section.getString("mode")).map(x -> Mode.valueOf(x.toUpperCase())).orElse(defaultConfig.mode())
			);
		}
	}
	
}
