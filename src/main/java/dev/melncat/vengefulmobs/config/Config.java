package dev.melncat.vengefulmobs.config;

import dev.melncat.vengefulmobs.VengefulMobs;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Creature;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class Config {
	private final VengefulMobs plugin;
	
	public Config(VengefulMobs plugin) {
		this.plugin = plugin;
	}
	
	private boolean vengefulMobsEnabled;
	private MobConfig defaultConfig;
	private EnumSet<EntityType> enabled;
	
	private final EnumMap<EntityType, MobConfig> overrides = new EnumMap<>(EntityType.class);
	private final EnumMap<EntityType, List<AttributeModifierConfig>> attributeModifiers = new EnumMap<>(EntityType.class);
	
	public void loadConfig(ConfigurationSection section) {
		vengefulMobsEnabled = section.getBoolean("enable-vengeful-mobs", true);
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

		attributeModifiers.clear();
		List<Map<?, ?>> rawModifiers = section.getMapList("attribute-modifiers");
		if (rawModifiers != null) {
			for (Map<?, ?> map : rawModifiers) {
				try {
					String mobStr = Objects.toString(map.get("mob"), null);
					if (mobStr == null) continue;
					EntityType mobType = EntityType.valueOf(mobStr.toUpperCase().trim());
					
					String attrStr = Objects.toString(map.get("attribute"), null);
					if (attrStr == null) continue;
					Attribute attribute = resolveAttribute(attrStr);
					if (attribute == null) {
						plugin.getLogger().warning("Invalid attribute: " + attrStr);
						continue;
					}
					
					String idStr = Objects.toString(map.get("id"), null);
					NamespacedKey idKey = resolveModifierId(idStr);
					
					double value = 0.0;
					Object valObj = map.get("value");
					if (valObj instanceof Number) {
						value = ((Number) valObj).doubleValue();
					} else if (valObj != null) {
						value = Double.parseDouble(valObj.toString());
					}
					
					String opStr = Objects.toString(map.get("operator"), "add_value");
					AttributeModifier.Operation operator = resolveOperation(opStr);
					
					AttributeModifierConfig modConfig = new AttributeModifierConfig(mobType, attribute, idKey, value, operator);
					attributeModifiers.computeIfAbsent(mobType, k -> new ArrayList<>()).add(modConfig);
				} catch (Exception e) {
					plugin.getLogger().warning("Failed to parse attribute modifier: " + map + " - " + e.getMessage());
				}
			}
		}
	}
	
	public boolean isVengefulMobsEnabled() {
		return vengefulMobsEnabled;
	}

	public boolean isEnabled(EntityType type) {
		return enabled.contains(type);
	}
	
	public @NotNull MobConfig fromType(EntityType type) {
		return overrides.getOrDefault(type, defaultConfig);
	}

	public @NotNull List<AttributeModifierConfig> getAttributeModifiers(EntityType type) {
		return attributeModifiers.getOrDefault(type, Collections.emptyList());
	}

	private Attribute resolveAttribute(String name) {
		if (name == null || name.isEmpty()) return null;
		String lower = name.toLowerCase().trim();
		
		// 1. Try resolving via NamespacedKey from string directly if it contains a colon
		if (lower.contains(":")) {
			NamespacedKey key = NamespacedKey.fromString(lower);
			if (key != null) {
				Attribute attr = Registry.ATTRIBUTE.get(key);
				if (attr != null) return attr;
			}
		}
		
		// 2. Try namespace minecraft with various variations
		String[] variations = {
			lower,
			lower.replace('_', '.'),
			"generic." + lower,
			"generic." + lower.replace('_', '.')
		};
		for (String var : variations) {
			NamespacedKey key = NamespacedKey.minecraft(var);
			Attribute attr = Registry.ATTRIBUTE.get(key);
			if (attr != null) return attr;
		}
		
		// 3. Scan the registry to match by name or key suffix to be fully flexible and avoid deprecated Enum/valueOf methods
		for (Attribute attr : Registry.ATTRIBUTE) {
			String attrKey = attr.getKey().getKey().toLowerCase(); // e.g. "generic.max_health"
			String cleanedKey = attrKey.replace(".", "").replace("_", ""); // e.g. "genericmaxhealth"
			String cleanedInput = lower.replace(".", "").replace("_", ""); // e.g. "maxhealth"
			
			if (cleanedKey.equalsIgnoreCase(cleanedInput) || 
				cleanedKey.equalsIgnoreCase("generic" + cleanedInput) ||
				attr.getKey().toString().equalsIgnoreCase(name)) {
				return attr;
			}
		}
		
		return null;
	}

	private AttributeModifier.Operation resolveOperation(String operator) {
		if (operator == null) return AttributeModifier.Operation.ADD_NUMBER;
		switch (operator.toLowerCase().trim()) {
			case "add_value":
			case "add_number":
			case "add":
				return AttributeModifier.Operation.ADD_NUMBER;
			case "add_multiplied_base":
			case "add_scalar":
				return AttributeModifier.Operation.ADD_SCALAR;
			case "add_multiplied_total":
			case "multiply_scalar_1":
				return AttributeModifier.Operation.MULTIPLY_SCALAR_1;
			default:
				try {
					return AttributeModifier.Operation.valueOf(operator.toUpperCase().trim());
				} catch (IllegalArgumentException e) {
					return AttributeModifier.Operation.ADD_NUMBER;
				}
		}
	}

	private NamespacedKey resolveModifierId(String idStr) {
		if (idStr == null || idStr.isEmpty()) {
			return new NamespacedKey(plugin, "default_modifier");
		}
		String lower = idStr.toLowerCase().trim();
		if (lower.contains(":")) {
			NamespacedKey key = NamespacedKey.fromString(lower);
			if (key != null) return key;
		}
		// Fallback: use plugin namespace, replacing invalid chars
		String sanitized = lower.replaceAll("[^a-z0-9._-]", "_");
		return new NamespacedKey(plugin, sanitized);
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

	public static class AttributeModifierConfig {
		private final EntityType mobType;
		private final Attribute attribute;
		private final NamespacedKey id;
		private final double value;
		private final AttributeModifier.Operation operator;

		public AttributeModifierConfig(EntityType mobType, Attribute attribute, NamespacedKey id, double value, AttributeModifier.Operation operator) {
			this.mobType = mobType;
			this.attribute = attribute;
			this.id = id;
			this.value = value;
			this.operator = operator;
		}

		public EntityType mobType() { return mobType; }
		public Attribute attribute() { return attribute; }
		public NamespacedKey id() { return id; }
		public double value() { return value; }
		public AttributeModifier.Operation operator() { return operator; }
	}
}

