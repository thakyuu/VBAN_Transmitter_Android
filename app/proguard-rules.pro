# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools.

# Keep VBAN header serialization (used via reflection/introspection in tests)
-keep class net.akyuu.thakyuu.vbantransmitter.VbanHeader { *; }
