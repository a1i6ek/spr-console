package kg.bakai.stopfactor.service;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;

import java.util.List;

/**
 * Stop factors in the console. Any change of a stop factor makes the next box (stop_factor_group) with copies of all
 * stop factors, the change applied, and closes the current one; the closed boxes stay for the checks made with them.
 * Nobody manages the boxes: a stop factor's history is read from them.
 */
public interface StopFactorConsoleService {

    /**
     * The stop factors of the current box, in check order.
     *
     * @param metricId   only stop factors of this metric
     * @param segment    stop factors of this segment or of every segment
     * @param productId  stop factors that check applications of this product
     * @param active     only active or only inactive ones
     * @param search     in the name or the description, ignoring case
     */
    List<StopFactorResponse> search(Long metricId, ProductSegment segment, Long productId,
                                    Boolean active, String search);

    /** The current version of a stop factor. */
    StopFactorResponse get(long factorId);

    /**
     * Every version of a stop factor, newest first, with how many scorings each one checked: its history. A deleted
     * stop factor has its history too.
     */
    List<StopFactorResponse> getVersions(long factorId);

    /** A new stop factor, checked last. */
    StopFactorResponse create(StopFactorRequest request);

    /** The next version of a stop factor with these values; nothing is made when nothing differs. */
    StopFactorResponse update(long factorId, StopFactorRequest request);

    /** Turns a stop factor on or off: its next version. */
    StopFactorResponse changeStatus(long factorId, boolean active, String changeReason);

    /** Ends a stop factor: its current version is closed with the reason, and it is checked no more. */
    void delete(long factorId, String changeReason);
}
