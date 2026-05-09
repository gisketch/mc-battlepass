package dev.gisketch.chowkingdom.compat;

import dev.gisketch.chowkingdom.revive.ReviveClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;

import java.util.UUID;

public final class ReviveRenderCompat {
    private static final String REVIVE_TEAM_NAME = "ck_revive_red";

    private ReviveRenderCompat() {
    }

    public static boolean isIncapacitated(Player player) {
        Team team = player.getTeam();
        if (team != null && REVIVE_TEAM_NAME.equals(team.getName())) return true;
        UUID playerId = player.getUUID();
        if (ReviveClientState.INSTANCE.progressForTarget(playerId) != null) return true;
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getUUID().equals(playerId) && ReviveClientState.INSTANCE.selfState() != null;
    }
}
