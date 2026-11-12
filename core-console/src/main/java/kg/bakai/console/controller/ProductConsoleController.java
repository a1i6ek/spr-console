package kg.bakai.console.controller;

import kg.bakai.product.dto.ProductDto;
import kg.bakai.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Credit products of SPR, read only. segment (from SPR's segmentType: BUSINESS is MSB, the others RETAIL) chooses
 * the stop factors of an application. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/products")
public class ProductConsoleController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(productService.getAll());
    }

    @GetMapping("/{productId}")
    public ResponseEntity<?> get(@PathVariable long productId) {
        return ResponseEntity.ok(productService.get(productId));
    }
}
