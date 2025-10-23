-- Add purgedAndDoubleBaggedGoodsEnclosed field to RMAs table
ALTER TABLE rmas ADD COLUMN purged_and_double_bagged_goods_enclosed BOOLEAN DEFAULT FALSE;

