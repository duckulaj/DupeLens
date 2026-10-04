package uk.co.dupelens.model;

import java.util.List;
import java.util.Set;

public record FileGroup(String id, String type, String title, String subtitle,
                        List<IndexedFile> files, Set<Long> suggestedDeleteIds, long reclaimableBytes) {}
