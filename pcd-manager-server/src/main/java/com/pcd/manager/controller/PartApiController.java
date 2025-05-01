package com.pcd.manager.controller;

import com.pcd.manager.model.Part;
import com.pcd.manager.service.PartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parts")
@CrossOrigin(origins = "*")
public class PartApiController {

    private final PartService partService;

    @Autowired
    public PartApiController(PartService partService) {
        this.partService = partService;
    }

    @GetMapping
    public ResponseEntity<List<Part>> getAllParts() {
        return ResponseEntity.ok(partService.getAllParts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Part> getPartById(@PathVariable Long id) {
        return partService.getPartById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Part> createPart(@RequestBody Part part) {
        part.setId(null); // Ensure a new entity is created
        Part savedPart = partService.savePart(part);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPart);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Part> updatePart(@PathVariable Long id, @RequestBody Part part) {
        return partService.getPartById(id)
                .map(existingPart -> {
                    part.setId(id);
                    Part updatedPart = partService.savePart(part);
                    return ResponseEntity.ok(updatedPart);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePart(@PathVariable Long id) {
        return partService.getPartById(id)
                .map(part -> {
                    partService.deletePart(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
} 