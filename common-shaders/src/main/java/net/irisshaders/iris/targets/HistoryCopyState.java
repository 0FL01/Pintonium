package net.irisshaders.iris.targets;

/** Lazy level-zero ownership. Higher mip levels always retain their physical owner. */
public final class HistoryCopyState {
	private boolean equal;
	private boolean untrackedAccess;
	private Runnable pendingCopy;
	private boolean separateMipChains;

	public void prepareMainSampling() {
		if (separateMipChains) materialize();
	}

	/** Called before generating OR enabling mipmaps on either physical side. */
	public void mipmapsRequested() {
		materialize();
		separateMipChains = true;
	}

	public boolean canSampleMainFromAlt() {
		return pendingCopy != null && !separateMipChains;
	}

	public void publish(Runnable copy) {
		if (!needsCopy()) return;
		pendingCopy = copy;
		if (untrackedAccess) materialize();
	}

	public boolean isMainAliasedToAlt() {
		return pendingCopy != null;
	}

	public void materialize() {
		if (pendingCopy == null) return;
		pendingCopy.run();
		pendingCopy = null;
		copied();
	}

	public boolean needsCopy() {
		return !equal || untrackedAccess;
	}

	public boolean hasUntrackedAccess() {
		return untrackedAccess;
	}

	public void copied() {
		equal = true;
	}

	/** Any possible write, including partial/blended draws and external clears. */
	public void mayWrite() {
		materialize();
		equal = false;
	}

	/** Images or exported FBO handles can write without a tracked framebuffer bind. */
	public void untrackedAccess() {
		materialize();
		untrackedAccess = true;
		equal = false;
	}

	public void resized() {
		pendingCopy = null;
		equal = false;
	}
}
