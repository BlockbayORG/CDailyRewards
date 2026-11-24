package carnage.cDailyRewards.reward;

import carnage.cDailyRewards.util.ItemParser;

import java.util.ArrayList;
import java.util.List;

public record RewardDay(int day, boolean milestone, List<Reward> rewards, ItemParser icon, String broadcast) {

    public List<String> displayLines() {
        List<String> lines = new ArrayList<>();
        for (Reward r : rewards) {
            String line = r.displayLine();
            if (line != null)
                lines.add(line);
        }
        if (lines.isEmpty())
            lines.add("<dark_gray>• <gray>???");
        return lines;
    }
}
