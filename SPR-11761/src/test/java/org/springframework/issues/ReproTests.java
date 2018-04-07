package org.springframework.issues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.issues.config.AppConfig;
import org.springframework.issues.data.Book;
import org.springframework.issues.service.BookService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AppConfig.class})
public class ReproTests {

    private static final Logger LOG = getLogger(ReproTests.class);

    @Autowired
    private BookService books;

    @Test
    public void test() throws InterruptedException {
        final Book book1 = new Book(1, "A");
        books.createBook(book1);
        final Book book2 = new Book(2, "B");
        books.createBook(book2);

        final Book book3 = new Book(3, "C");
        books.createBookWithoutTx(book3);

        final Book bookAfterCommit = new Book(55, "AFTER COMMIT");
        books.triggerCreatePostCommit(bookAfterCommit);
        final Book book4 = new Book(4, "D");
        books.createBook(book4);

        assertThat(book1).isEqualTo(books.lookupBookById(1));
        assertThat(book2).isEqualTo(books.lookupBookById(2));

        // Book triggered post commit is found
        assertThat(books.lookupBookById(55)).isEqualTo(bookAfterCommit);
        // Books after the post commit are found within the same thread (later on, in a separate thread this is not found!)
        assertThat(books.lookupBookById(4)).isEqualTo(book4);
        // Even the older ones
        assertThat(books.lookupBookById(3)).isEqualTo(book3);

        final AtomicBoolean foundBook4 = new AtomicBoolean(false);
        final AtomicBoolean foundBookAfterCommit = new AtomicBoolean(false);

        final Thread thread = new Thread(() -> {
            // Books that were written after the post commit are not found while executing in a separate thread (not even in DB)!! This indicates
            // that the framework reported that it was saved to the database but in reality only within the thread's memory!
            final Book bookLookup4 = books.lookupBookById(4);
            foundBook4.set(bookLookup4 != null);
            LOG.info("Book by Id 4 : " + bookLookup4);
            final Book bookLookup55 = books.lookupBookById(55);
            foundBookAfterCommit.set(bookLookup55 != null);
            LOG.info("Book by Id 55 : " + bookLookup55);
        });

        thread.start();
        thread.join();

        assertThat(foundBookAfterCommit.get()).isTrue();
        assertThat(foundBook4.get()).isTrue(); // Should fail since book was not found in separate thread
    }
}
