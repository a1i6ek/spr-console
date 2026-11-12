package kg.bakai.console.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.bakai.product.dto.ProductDto;
import kg.bakai.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Products", description = "Credit products of SPR, read only. segmentType (RETAIL / MSB) chooses "
        + "the stop factors of an application")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/products")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "All products")
    @GetMapping
    public List<ProductDto> list() {
        return productService.getAll();
    }

    @Operation(summary = "One product")
    @GetMapping("/{productId}")
    public ProductDto get(@PathVariable long productId) {
        return productService.get(productId);
    }
}
