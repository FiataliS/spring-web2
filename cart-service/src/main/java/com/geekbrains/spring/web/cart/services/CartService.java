package com.geekbrains.spring.web.cart.services;

import com.geekbrains.spring.web.cart.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {
    @Qualifier("test")
    private final CacheManager cacheManager;
    private final RestTemplate restTemplate; //ProductService
    private final KafkaTemplate<Long, OrderDto> kafkaTemplate;
    @Value("${spring.cache.user.name}")
    private String CACHE_CART;
    @Value("spring.kafka.topic")
    private String topic;
    private Cart cart;

    @Cacheable(value = "Cart", key = "#cartName")
    public Cart getCurrentCart(String cartName){
        cart = cacheManager.getCache(CACHE_CART).get(cartName, Cart.class);
        if(!Optional.ofNullable(cart).isPresent()){
            cart = new Cart(cartName, cacheManager);
            cacheManager.getCache(CACHE_CART).put(cartName, cart);
        }
        return cart;
    }

    @CachePut(value = "Cart", key = "#cartName")
    public Cart addProductByIdToCart(Long id, String cartName){
        Cart cart = getCurrentCart(cartName);
        if(!cart.addProductCount(id)) {
            ProductDto product =
                    restTemplate.getForObject("http://localhost:8189/web-market-core/api/v1/products/" + id, ProductDto.class);
            cart.addProduct(product);
        }
            return cart;
    }

    @CachePut(value = "Cart", key = "#cartName")
    public Cart clear(String cartName){
        Cart cart = getCurrentCart(cartName);
        cart.clear();
        return cart;
    }

    public void createOrder(String username, OrderDetailsDto orderDetailsDto, String cartName) {
        Cart currentCart = getCurrentCart(cartName);
        OrderDto order = new OrderDto();
        order.setAddress(orderDetailsDto.getAddress());
        order.setPhone(orderDetailsDto.getPhone());
        order.setUsername(username);
        order.setTotalPrice(currentCart.getTotalPrice());
        order.setItemDtoList(currentCart.getItems());
        currentCart.clear();
        kafkaTemplate.send(topic, order);
    }
}
