package org.example.realtimestats.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.realtimestats.entity.GamePlayer;
import org.example.realtimestats.service.GamePlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏玩家控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/game/player")
public class GamePlayerController {

    @Autowired
    private GamePlayerService gamePlayerService;

    /**
     * 创建游戏玩家
     */
    @PostMapping
    public ResponseEntity<Long> createPlayer(@RequestBody GamePlayer gamePlayer) {
        try {
            boolean saved = gamePlayerService.save(gamePlayer);
            Long playerId = saved ? gamePlayer.getId() : null;
            return ResponseEntity.ok(playerId);
        } catch (Exception e) {
            log.error("创建游戏玩家失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新玩家信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<Boolean> updatePlayer(@PathVariable("id") Long id, @RequestBody GamePlayer gamePlayer) {
        try {
            gamePlayer.setId(id);
            boolean result = gamePlayerService.updateById(gamePlayer);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("更新玩家信息失败，id=" + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 增加玩家积分
     */
    @PostMapping("/{id}/score")
    public ResponseEntity<Map<String, Object>> incrementScore(
            @PathVariable("id") Long playerId,
            @RequestParam("score") Long score) {
        try {
            Long newScore = gamePlayerService.incrementScore(playerId, score);
            Integer rank = gamePlayerService.getPlayerRank(playerId);

            Map<String, Object> result = new HashMap<>();
            result.put("playerId", playerId);
            result.put("score", newScore);
            result.put("rank", rank);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("增加玩家积分失败，playerId=" + playerId + "，score=" + score, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取玩家当前积分
     */
    @GetMapping("/{id}/score")
    public ResponseEntity<Long> getPlayerScore(@PathVariable("id") Long playerId) {
        try {
            Long score = gamePlayerService.getScore(playerId);
            return ResponseEntity.ok(score);
        } catch (Exception e) {
            log.error("获取玩家积分失败，playerId=" + playerId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取玩家排名
     */
    @GetMapping("/{id}/rank")
    public ResponseEntity<Integer> getPlayerRank(@PathVariable("id") Long playerId) {
        try {
            Integer rank = gamePlayerService.getPlayerRank(playerId);
            return ResponseEntity.ok(rank);
        } catch (Exception e) {
            log.error("获取玩家排名失败，playerId=" + playerId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取积分排行榜
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<List<GamePlayer>> getScoreLeaderboard(
            @RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        try {
            List<GamePlayer> leaderboard = gamePlayerService.getTopPlayers(limit);
            return ResponseEntity.ok(leaderboard);
        } catch (Exception e) {
            log.error("获取积分排行榜失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 手动触发同步积分数据到数据库
     */
    @PostMapping("/sync")
    public ResponseEntity<Void> syncScoreData() {
        try {
            gamePlayerService.syncScoresToDb();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("同步积分数据失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}