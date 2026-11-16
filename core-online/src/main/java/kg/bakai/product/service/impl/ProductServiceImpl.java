package kg.bakai.product.service.impl;

import kg.bakai.common.exception.NotFoundException;
import kg.bakai.product.dto.ProductDto;
import kg.bakai.product.entity.Product;
import kg.bakai.product.repository.ProductRepository;
import kg.bakai.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getAll() {
        return productRepository.findAllOrderById().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDto get(long id) {
        return productRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Продукт " + id + " не найден"));
    }

    private ProductDto toDto(Product product) {
        return new ProductDto(product.getId(), product.getName(), product.getDescription(), product.getSegmentType(), product.getSegment(),
                product.getCreatedDate(), product.getUpdatedDate());
    }
}
