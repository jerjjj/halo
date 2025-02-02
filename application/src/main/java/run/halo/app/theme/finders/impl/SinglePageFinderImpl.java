package run.halo.app.theme.finders.impl;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.content.SinglePageService;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.SinglePage;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.theme.finders.Finder;
import run.halo.app.theme.finders.SinglePageConversionService;
import run.halo.app.theme.finders.SinglePageFinder;
import run.halo.app.theme.finders.vo.ContentVo;
import run.halo.app.theme.finders.vo.ListedSinglePageVo;
import run.halo.app.theme.finders.vo.SinglePageVo;

/**
 * A default implementation of {@link SinglePage}.
 *
 * @author guqing
 * @since 2.0.0
 */
@Finder("singlePageFinder")
@AllArgsConstructor
public class SinglePageFinderImpl implements SinglePageFinder {

    public static final Predicate<SinglePage> FIXED_PREDICATE = page -> page.isPublished()
        && Objects.equals(false, page.getSpec().getDeleted())
        && Post.VisibleEnum.PUBLIC.equals(page.getSpec().getVisible());

    private final ReactiveExtensionClient client;

    private final SinglePageConversionService singlePagePublicQueryService;

    private final SinglePageService singlePageService;

    @Override
    public Mono<SinglePageVo> getByName(String pageName) {
        return client.get(SinglePage.class, pageName)
            .filter(FIXED_PREDICATE)
            .flatMap(singlePagePublicQueryService::convertToVo);
    }

    @Override
    public Mono<ContentVo> content(String pageName) {
        return singlePageService.getReleaseContent(pageName)
            .map(wrapper -> ContentVo.builder().content(wrapper.getContent())
                .raw(wrapper.getRaw()).build());
    }

    @Override
    public Mono<ListResult<ListedSinglePageVo>> list(Integer page, Integer size) {
        return list(page, size, null, null);
    }

    @Override
    public Mono<ListResult<ListedSinglePageVo>> list(@Nullable Integer page, @Nullable Integer size,
        @Nullable Predicate<SinglePage> predicate, @Nullable Comparator<SinglePage> comparator) {
        var predicateToUse = Optional.ofNullable(predicate)
            .map(p -> p.and(FIXED_PREDICATE))
            .orElse(FIXED_PREDICATE);
        var comparatorToUse = Optional.ofNullable(comparator)
            .orElse(defaultComparator());
        return client.list(SinglePage.class, predicateToUse,
                comparatorToUse, pageNullSafe(page), sizeNullSafe(size))
            .flatMap(list -> Flux.fromStream(list.get())
                .concatMap(singlePagePublicQueryService::convertToListedVo)
                .collectList()
                .map(pageVos -> new ListResult<>(list.getPage(), list.getSize(), list.getTotal(),
                    pageVos)
                )
            )
            .defaultIfEmpty(new ListResult<>(0, 0, 0, List.of()));
    }

    static Comparator<SinglePage> defaultComparator() {
        Function<SinglePage, Boolean> pinned =
            page -> Objects.requireNonNullElse(page.getSpec().getPinned(), false);
        Function<SinglePage, Integer> priority =
            page -> Objects.requireNonNullElse(page.getSpec().getPriority(), 0);
        Function<SinglePage, Instant> creationTimestamp =
            page -> page.getMetadata().getCreationTimestamp();
        Function<SinglePage, String> name = page -> page.getMetadata().getName();
        return Comparator.comparing(pinned)
            .thenComparing(priority)
            .thenComparing(creationTimestamp)
            .thenComparing(name)
            .reversed();
    }

    int pageNullSafe(Integer page) {
        return ObjectUtils.defaultIfNull(page, 1);
    }

    int sizeNullSafe(Integer size) {
        return ObjectUtils.defaultIfNull(size, 10);
    }
}
