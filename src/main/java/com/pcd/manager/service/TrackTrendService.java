package com.pcd.manager.service;

import com.pcd.manager.model.TrackTrend;
import com.pcd.manager.model.Tool;
import com.pcd.manager.repository.TrackTrendRepository;
import com.pcd.manager.repository.ToolRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class TrackTrendService {

    private final TrackTrendRepository trackTrendRepository;
    private final ToolRepository toolRepository;

    @Autowired
    public TrackTrendService(TrackTrendRepository trackTrendRepository, ToolRepository toolRepository) {
        this.trackTrendRepository = trackTrendRepository;
        this.toolRepository = toolRepository;
    }

    public List<TrackTrend> getAllTrackTrends() {
        return trackTrendRepository.findAll();
    }

    public Optional<TrackTrend> getTrackTrendById(Long id) {
        return trackTrendRepository.findById(id);
    }

    public TrackTrend saveTrackTrend(TrackTrend trackTrend) {
        return trackTrendRepository.save(trackTrend);
    }

    public void deleteTrackTrend(Long id) {
        trackTrendRepository.deleteById(id);
    }

    /**
     * Fetch all tools for selection in forms.
     */
    public List<Tool> getAllTools() {
        return toolRepository.findAll();
    }

    /**
     * Get all TrackTrends that include the given tool
     */
    public List<TrackTrend> getTrackTrendsByToolId(Long toolId) {
        return trackTrendRepository.findByAffectedToolsId(toolId);
    }
} 