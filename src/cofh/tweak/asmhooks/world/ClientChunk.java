package cofh.tweak.asmhooks.world;

import cofh.repack.cofh.lib.util.LinkedHashList;
import cofh.repack.cofh.lib.util.SynchronizedIdentityLinkedHashList;
import cofh.repack.net.minecraft.client.renderer.chunk.SetVisibility;
import cofh.repack.net.minecraft.client.renderer.chunk.VisGraph;
import cofh.tweak.CoFHTweaks;
import cofh.tweak.asmhooks.render.RenderGlobal;
import cpw.mods.fml.common.FMLLog;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class ClientChunk extends Chunk {

	private static ChunkThread worker = new ChunkThread();
	static {
		worker.start();
	}

	private static class ChunkThread extends Thread {

		public ChunkThread() {

			super("Chunk Worker");
		}

		public LinkedHashList<ClientChunk> loaded = new SynchronizedIdentityLinkedHashList<ClientChunk>();
		public LinkedHashList<ClientChunk> modified = new SynchronizedIdentityLinkedHashList<ClientChunk>();

		@Override
		public void run() {

			for (;;) {
				int i = 0;
				boolean work = false;
				for (; loaded.size() > 0; ++i) {
					ClientChunk chunk = loaded.shift().buildSides();
					if (chunk != null) {
						modified.add(chunk);
						work = true;
					}
					if ((i & 3) == 0) {
						yield();
					}
				}
				for (i = 0; modified.size() > 0; ++i) {
					ClientChunk chunk = modified.shift();
					if (loaded.contains(chunk)) {
						continue;
					}
					for (VisGraph graph : chunk.visibility) {
						if (graph.isDirty()) {
							SetVisibility a = graph.getVisibility();
							graph.computeVisibility();
							work |= !a.equals(graph.getVisibility());
						}
					}
					if ((i & 7) == 0) {
						yield();
					}
				}
				RenderGlobal.worker.dirty = work;
				try {
					Thread.sleep(30);
				} catch (InterruptedException e) {
				}
			}
		}
	}

	public VisGraph[] visibility = new VisGraph[16];

	private static void init(VisGraph[] internalSides) {

		for (int i = 0; i < 16; ++i) {
			internalSides[i] = new VisGraph();
		}
		if (!CoFHTweaks.canHaveWorld()) {
			FMLLog.bigWarning("World exists prior to starting the server!");
		}
	}

	public ClientChunk(World world, int x, int z) {

		super(world, x, z);
		init(visibility);
	}

	public ClientChunk(World world, Block[] blocks, int x, int z) {

		super(world, blocks, x, z);
		init(visibility);
	}

	public ClientChunk(World world, Block[] blocks, byte[] metas, int x, int z) {

		super(world, blocks, metas, x, z);
		init(visibility);
	}

	boolean checkPosSolid(int x, int y, int z, Block block) {

		if (y > 255 || y < 0)
			return false;
		if (block == null) {
			block = getBlock(x, y, z);
		}
		VisGraph chunk = this.visibility[y >> 4];
		y &= 15;

		chunk.setOpaque(x, y, z, block.isOpaqueCube());
		return chunk.isDirty();
	}

	@Override
	public boolean func_150807_a(int x, int y, int z, Block block, int meta) {

		boolean r = super.func_150807_a(x, y, z, block, meta);
		if (r && checkPosSolid(x & 15, y, z & 15, block)) {
			worker.modified.add(this);
		}
		return r;
	}

	@Override
	public void fillChunk(byte[] data, int x, int z, boolean flag) {

		super.fillChunk(data, x, z, flag);
		worker.loaded.add(this);
	}

	ClientChunk buildSides() {

		if (!this.worldObj.chunkExists(xPosition, zPosition)) {
			return null;
		}
		for (int i = 0; i < 16; ++i) {
			for (int j = 0; j < 16; ++j) {
				for (int y = 0; y < 256; ++y) {
					checkPosSolid(i, y, j, null);
				}
			}
		}
		RenderGlobal.updateArea(xPosition * 16 - 1, 0, zPosition * 16 - 1, xPosition * 16 + 16, 255, zPosition * 16 + 16);
		return this;
	}

}
