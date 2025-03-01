package net.notfab.ttvsi.client.models.twitch;

/**
 * Twitch says someone claimed a reward
 *
 * @param id        Redeem ID
 * @param reward    Twitch Reward ID
 * @param user_code User's login code
 * @param user_name User's display name
 * @param input     Optional reward input
 */
public record TwitchRewardEvent(String id, String reward, String user_code, String user_name, String input) {
}
