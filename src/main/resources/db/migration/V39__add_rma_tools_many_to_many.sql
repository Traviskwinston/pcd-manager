-- Create join table for RMA <-> Tools (affected tools)
CREATE TABLE IF NOT EXISTS rma_tools (
    rma_id BIGINT NOT NULL,
    tool_id BIGINT NOT NULL,
    PRIMARY KEY (rma_id, tool_id),
    CONSTRAINT fk_rma_tools_rma FOREIGN KEY (rma_id) REFERENCES rmas (id) ON DELETE CASCADE,
    CONSTRAINT fk_rma_tools_tool FOREIGN KEY (tool_id) REFERENCES tools (id) ON DELETE CASCADE
);

-- Backfill: for existing rows that have legacy rmas.tool_id, add into rma_tools
INSERT INTO rma_tools (rma_id, tool_id)
SELECT id AS rma_id, tool_id
FROM rmas
WHERE tool_id IS NOT NULL
ON CONFLICT DO NOTHING;




