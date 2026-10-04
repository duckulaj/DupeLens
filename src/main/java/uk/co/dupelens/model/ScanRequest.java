package uk.co.dupelens.model;
import java.util.List;
public record ScanRequest(List<String> roots, boolean recursive, boolean includeHidden, boolean aiEnabled) {}
