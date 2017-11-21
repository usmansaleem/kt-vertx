// script.js

// create the module and name it blogApp
var blogApp = angular.module('blogApp', ['ngRoute','ngAnimate','ui.bootstrap', 'ngSanitize', 'viewhead']);

//ng-routes
blogApp.config(function($routeProvider) {
        $routeProvider

            // route for the home page
            .when('/', {
                templateUrl : 'pages/home.html',
                controller  : 'mainCtrl'
            })

            // route for the about page
            .when('/about', {
                templateUrl : 'pages/about.html',
                controller  : 'aboutCtrl'
            })

            .otherwise({
                           redirectTo: '/'
                        });

    });

blogApp.directive('readmoreDirective', function() {
    return function(scope, element, attrs) {
	scope.$watch('blogItem', function(){
		angular.element('article').readmore();
	});	

    }
});

blogApp.directive('gistDirective', function() {
    return function(scope, element, attrs) {
        scope.$watch('blogItem', function(){
            angular.element('[data-gist-id]').gist();
        });

    }
});



// create the controller and inject Angular's $scope
blogApp.controller('mainCtrl', function($scope, $http, $log, $sce) {
    $scope.trustBlogHtml = function(html) {
              return $sce.trustAsHtml(html);
            };

    //when page changes, fetch new data
    $scope.pageChanged = function() {
        $http.get('rest/blog/blogItems/' + $scope.bigCurrentPage).then(function(response) {
                   $scope.blogItems = response.data;
                   window.scrollTo(0,0);
            });
      };

    //function to get updated blog count, specially when section changes
    $scope.getBlogCount = function() {
       $http.get('rest/blog/blogCount').then(function(response) {
                      $scope.bigTotalItems = response.data;
                      $scope.pageChanged();
           });
    };

    //function to get highestPage
    $scope.getHighestPage = function() {
        $http.get('rest/blog/highestPage').then(function(response) {
            $scope.lastPage =  response.data;
            $scope.getBlogCount()
        });
    };

    //function to show jumbotron
    $scope.showJumbotron = function() {
        return $scope.bigCurrentPage == 1
    };

    $scope.pageClass = 'page-home';
    $scope.maxSize = 5;
    $scope.bigCurrentPage = 1;
    $scope.lastPage = 1;

    //call method chain ...
    $scope.getHighestPage();
    
});

// create the controller and inject Angular's $scope
blogApp.controller('aboutCtrl', function($scope) {
    $scope.pageClass = 'page-about';
});
