package dev.melncat.vengefulmobs.listener;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Creature;
import org.jetbrains.annotations.NotNull;
import java.util.EnumSet;

public class PaperVanillaGoal<T extends Creature> implements Goal<T> {
	private final net.minecraft.world.entity.ai.goal.Goal nmsGoal;
	private final GoalKey<T> key;
	private final EnumSet<GoalType> types;

	public PaperVanillaGoal(net.minecraft.world.entity.ai.goal.Goal nmsGoal) {
		this.nmsGoal = nmsGoal;
		this.key = GoalKey.of((Class<T>) Creature.class, NamespacedKey.randomKey());
		this.types = EnumSet.noneOf(GoalType.class);
		ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<net.minecraft.world.entity.ai.goal.Goal.Flag> flags = nmsGoal.getFlags();
		for (net.minecraft.world.entity.ai.goal.Goal.Flag flag : net.minecraft.world.entity.ai.goal.Goal.Flag.values()) {
			if (flags.hasElement(flag)) {
				switch (flag) {
					case MOVE -> this.types.add(GoalType.MOVE);
					case LOOK -> this.types.add(GoalType.LOOK);
					case JUMP -> this.types.add(GoalType.JUMP);
					case TARGET -> this.types.add(GoalType.TARGET);
				}
			}
		}
	}

	@Override
	public boolean shouldActivate() {
		return nmsGoal.canUse();
	}

	@Override
	public boolean shouldStayActive() {
		return nmsGoal.canContinueToUse();
	}

	@Override
	public void start() {
		nmsGoal.start();
	}

	@Override
	public void stop() {
		nmsGoal.stop();
	}

	@Override
	public void tick() {
		nmsGoal.tick();
	}

	@Override
	public @NotNull GoalKey<T> getKey() {
		return key;
	}

	@Override
	public @NotNull EnumSet<GoalType> getTypes() {
		return types;
	}
}
