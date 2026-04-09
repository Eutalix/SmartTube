package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.mediaserviceinterfaces.data.EndScreenItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;

import java.util.ArrayList;
import java.util.List;

public class EndScreenController extends BasePlayerController {
    private static final String TAG = EndScreenController.class.getSimpleName();
    private List<EndScreenItem> mEndScreenItems;
    private boolean mEndScreenShown;
    private long mVideoDurationMs;
    private final Runnable mEndScreenHandler = this::checkAndShowEndScreen;

    @Override
    public void onInit() {
        // NOP
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        if (metadata == null || getPlayer() == null) {
            return;
        }

        // Store end screen items from metadata
        mEndScreenItems = metadata.getEndScreenItems();
        
        if (mEndScreenItems != null && !mEndScreenItems.isEmpty()) {
            Log.d(TAG, "Loaded %d end screen items", mEndScreenItems.size());
        }
    }

    @Override
    public void onVideoLoaded(Video item) {
        if (getPlayer() == null) {
            return;
        }

        mVideoDurationMs = getPlayer().getDurationMs();
        mEndScreenShown = false;
        
        // Start monitoring playback position
        startEndScreenMonitoring();
    }

    @Override
    public void onEngineReleased() {
        hideEndScreen();
        stopEndScreenMonitoring();
    }

    @Override
    public void onFinish() {
        hideEndScreen();
        stopEndScreenMonitoring();
        mEndScreenItems = null;
    }

    @Override
    public void onControlsShown(boolean shown) {
        // Hide end screen when controls are shown
        if (shown && mEndScreenShown) {
            hideEndScreen();
        }
    }

    @Override
    public void onSeekEnd() {
        // Re-evaluate end screen visibility after seek
        checkAndShowEndScreen();
    }

    /**
     * Called periodically to check if end screen should be shown
     */
    private void checkAndShowEndScreen() {
        if (getPlayer() == null || mEndScreenItems == null || mEndScreenItems.isEmpty()) {
            return;
        }

        long currentPositionMs = getPlayer().getPositionMs();
        
        // Find items that should be visible at current position
        List<EndScreenItem> visibleItems = getVisibleItemsAtPosition(currentPositionMs);

        if (!visibleItems.isEmpty() && !mEndScreenShown && !getPlayer().isControlsShown()) {
            showEndScreen(visibleItems);
        } else if (visibleItems.isEmpty() && mEndScreenShown) {
            hideEndScreen();
        }

        // Schedule next check
        scheduleNextCheck(currentPositionMs);
    }

    /**
     * Get end screen items that should be visible at given position
     */
    private List<EndScreenItem> getVisibleItemsAtPosition(long positionMs) {
        List<EndScreenItem> visible = new ArrayList<>();

        if (mEndScreenItems == null) {
            return visible;
        }

        for (EndScreenItem item : mEndScreenItems) {
            long startMs = item.getStartTimeMs();
            long endMs = item.getEndTimeMs();

            // Check if item should be visible at current position
            if (positionMs >= startMs && (endMs == 0 || positionMs <= endMs)) {
                visible.add(item);
            }
        }

        return visible;
    }

    /**
     * Calculate when the next end screen check should occur
     */
    private void scheduleNextCheck(long currentPositionMs) {
        if (mEndScreenItems == null || getPlayer() == null) {
            return;
        }

        long nextCheckDelayMs = 1000; // Default 1 second

        // Find the nearest start or end time
        for (EndScreenItem item : mEndScreenItems) {
            long startMs = item.getStartTimeMs();
            long endMs = item.getEndTimeMs();

            if (startMs > currentPositionMs) {
                long delayToStart = startMs - currentPositionMs;
                nextCheckDelayMs = Math.min(nextCheckDelayMs, delayToStart);
            }

            if (endMs > 0 && endMs > currentPositionMs) {
                long delayToEnd = endMs - currentPositionMs;
                nextCheckDelayMs = Math.min(nextCheckDelayMs, delayToEnd);
            }
        }

        // Account for playback speed
        float speed = getPlayer().getSpeed();
        if (speed > 0) {
            nextCheckDelayMs = (long) (nextCheckDelayMs / speed);
        }

        Helpers.postDelayed(mEndScreenHandler, nextCheckDelayMs);
    }

    /**
     * Show end screen overlay with items
     */
    private void showEndScreen(List<EndScreenItem> items) {
        if (getPlayer() == null) {
            return;
        }

        Log.d(TAG, "Showing end screen with %d items", items.size());
        
        getPlayer().showEndScreen(items);
        
        mEndScreenShown = true;
    }

    /**
     * Hide end screen overlay
     */
    private void hideEndScreen() {
        if (getPlayer() == null || !mEndScreenShown) {
            return;
        }

        Log.d(TAG, "Hiding end screen");
        
        getPlayer().hideEndScreen();
        
        mEndScreenShown = false;
    }

    /**
     * Start monitoring playback for end screen timing
     */
    private void startEndScreenMonitoring() {
        stopEndScreenMonitoring();
        Helpers.postDelayed(mEndScreenHandler, 100);
    }

    /**
     * Stop monitoring playback
     */
    private void stopEndScreenMonitoring() {
        Helpers.removeCallbacks(mEndScreenHandler);
    }

    /**
     * Handle end screen item click
     */
    public void onEndScreenItemClicked(EndScreenItem item) {
        if (item == null || getPlayer() == null) {
            return;
        }

        Log.d(TAG, "End screen item clicked: type=%d, id=%s", item.getType(), item.getId());

        // Create video from end screen item
        Video video = createVideoFromEndScreenItem(item);
        
        if (video != null) {
            // Open the video/channel/playlist
            getPlaybackPresenter().openVideo(video);
        }
    }

    /**
     * Convert EndScreenItem to Video object
     */
    private Video createVideoFromEndScreenItem(EndScreenItem item) {
        Video video = new Video();

        switch (item.getType()) {
            case EndScreenItem.TYPE_VIDEO:
                video.videoId = item.getId();
                video.title = item.getTitle();
                video.cardImageUrl = item.getImageUrl();
                break;
            case EndScreenItem.TYPE_CHANNEL:
                video.channelId = item.getId();
                video.title = item.getTitle();
                video.cardImageUrl = item.getImageUrl();
                break;
            case EndScreenItem.TYPE_PLAYLIST:
                video.playlistId = item.getId();
                video.title = item.getTitle();
                video.cardImageUrl = item.getImageUrl();
                break;
            default:
                Log.w(TAG, "Unknown end screen item type: %d", item.getType());
                return null;
        }

        return video;
    }
}