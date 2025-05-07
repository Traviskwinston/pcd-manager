package com.pcd.manager.controller;

import com.pcd.manager.model.Tool;
import com.pcd.manager.service.ToolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
@CrossOrigin(origins = "*")
public class ToolApiController {

    private final ToolService toolService;

    @Autowired
    public ToolApiController(ToolService toolService) {
        this.toolService = toolService;
    }

    @GetMapping
    public ResponseEntity<List<Tool>> getAllTools() {
        return ResponseEntity.ok(toolService.getAllTools());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tool> getToolById(@PathVariable Long id) {
        return toolService.getToolById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Tool> createTool(@RequestBody Tool tool) {
        tool.setId(null); // Ensure a new entity is created
        Tool savedTool = toolService.saveTool(tool);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedTool);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Tool> updateTool(@PathVariable Long id, @RequestBody Tool tool) {
        return toolService.getToolById(id)
                .map(existingTool -> {
                    tool.setId(id);
                    Tool updatedTool = toolService.saveTool(tool);
                    return ResponseEntity.ok(updatedTool);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTool(@PathVariable Long id) {
        return toolService.getToolById(id)
                .map(tool -> {
                    toolService.deleteTool(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
} 